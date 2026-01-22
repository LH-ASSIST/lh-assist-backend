package com.lh.assist.suggestion.application;

import com.lh.assist.suggestion.domain.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionViewCountService {

    private static final String VIEW_KEY_PREFIX = "suggestion:view:";
    private static final String VIEW_KEY_SET = "suggestion:view:keys";

    private final StringRedisTemplate stringRedisTemplate;
    private final SuggestionRepository suggestionRepository;

    public void increment(Long suggestionId) {
        String key = VIEW_KEY_PREFIX + suggestionId;
        stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.opsForSet().add(VIEW_KEY_SET, key);
    }

    @Scheduled(fixedDelayString = "${app.suggestion.view-count.flush-interval-ms:60000}")
    @Transactional
    public void flushToDatabase() {
        Set<String> keys = stringRedisTemplate.opsForSet().members(VIEW_KEY_SET);
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            String value = stringRedisTemplate.opsForValue().getAndSet(key, "0");
            int delta = parseDelta(value);
            if (delta <= 0) {
                continue;
            }
            try {
                flushKey(key, delta);
            } catch (Exception ex) {
                restoreDelta(key, delta);
                log.warn("조회수 반영에 실패했습니다. key={}, delta={}", key, delta, ex);
            }
        }
    }

    private void flushKey(String key, int delta) {
        Long suggestionId = parseSuggestionId(key);
        if (suggestionId == null) {
            stringRedisTemplate.opsForSet().remove(VIEW_KEY_SET, key);
            return;
        }
        int updated = suggestionRepository.incrementViewCount(suggestionId, delta);
        if (updated == 0) {
            restoreDelta(key, delta);
            return;
        }
        removeKeyIfEmpty(key);
    }

    private int parseDelta(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Long parseSuggestionId(String key) {
        if (key == null || !key.startsWith(VIEW_KEY_PREFIX)) {
            return null;
        }
        String idPart = key.substring(VIEW_KEY_PREFIX.length());
        try {
            return Long.parseLong(idPart);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void restoreDelta(String key, int delta) {
        stringRedisTemplate.opsForValue().increment(key, delta);
        stringRedisTemplate.opsForSet().add(VIEW_KEY_SET, key);
    }

    private void removeKeyIfEmpty(String key) {
        String current = stringRedisTemplate.opsForValue().get(key);
        if (current == null || "0".equals(current)) {
            stringRedisTemplate.opsForSet().remove(VIEW_KEY_SET, key);
        }
    }
}
