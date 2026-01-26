package com.lh.assist.suggestion.application;

import com.lh.assist.suggestion.domain.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionViewCountService {

    private static final String VIEW_KEY_PREFIX = "suggestion:view:";
    private static final String VIEW_KEY_SET = "suggestion:view:keys";
    private static final Duration VIEW_KEY_TTL = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;
    private final SuggestionRepository suggestionRepository;

    /**
     * Redis에 조회수 증가를 기록한다
     *
     * @param suggestionId 조회 대상 건의 ID
     */
    public void increment(Long suggestionId) {
        String key = VIEW_KEY_PREFIX + suggestionId;
        stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.opsForSet().add(VIEW_KEY_SET, key);
        refreshKeyTtl(key);
    }

    public int getViewCount(Long suggestionId, int baseCount) {
        String key = VIEW_KEY_PREFIX + suggestionId;
        String value = stringRedisTemplate.opsForValue().get(key);
        int delta = parseDelta(value);
        return baseCount + Math.max(delta, 0);
    }

    public Map<Long, Integer> getViewCountDeltas(List<Long> suggestionIds) {
        if (suggestionIds == null || suggestionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> keys = suggestionIds.stream()
                .map(id -> VIEW_KEY_PREFIX + id)
                .toList();
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        Map<Long, Integer> deltas = new HashMap<>();
        if (values == null) {
            return deltas;
        }
        for (int i = 0; i < keys.size(); i++) {
            String value = values.get(i);
            int delta = parseDelta(value);
            if (delta > 0) {
                deltas.put(suggestionIds.get(i), delta);
            }
        }
        return deltas;
    }

    /**
     * Redis에 누적된 조회수를 주기적으로 DB에 반영한다
     *
     * 반영 실패 시 해당 키의 증가분을 복구한다
     */
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
                removeKeyIfEmpty(key);
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

    /**
     * 특정 키의 증가분을 DB에 반영한다
     *
     * @param key 조회수 키
     * @param delta 반영할 증가분
     */
    private void flushKey(String key, int delta) {
        Long suggestionId = parseSuggestionId(key);
        if (suggestionId == null) {
            stringRedisTemplate.opsForSet().remove(VIEW_KEY_SET, key);
            return;
        }
        int updated = suggestionRepository.incrementViewCount(suggestionId, delta);
        if (updated == 0) {
            removeKey(key);
            log.warn("삭제된 건의사항으로 조회수 반영이 중단되었습니다. key={}", key);
            return;
        }
        removeKeyIfEmpty(key);
    }

    /**
     * Redis에 저장된 증가분 문자열을 정수로 변환한다
     *
     * @param value 증가분 문자열
     * @return 파싱된 증가분 값
     */
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

    /**
     * 조회수 키에서 건의 ID를 파싱한다
     *
     * @param key 조회수 키
     * @return 파싱된 건의 ID
     */
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

    /**
     * 반영 실패한 증가분을 Redis에 복구한다
     *
     * @param key 조회수 키
     * @param delta 복구할 증가분
     */
    private void restoreDelta(String key, int delta) {
        stringRedisTemplate.opsForValue().increment(key, delta);
        stringRedisTemplate.opsForSet().add(VIEW_KEY_SET, key);
        refreshKeyTtl(key);
    }

    /**
     * 증가분이 0이면 조회수 키를 정리한다
     *
     * @param key 조회수 키
     */
    private void removeKeyIfEmpty(String key) {
        String current = stringRedisTemplate.opsForValue().get(key);
        if (current == null || "0".equals(current)) {
            removeKey(key);
        }
    }

    private void removeKey(String key) {
        stringRedisTemplate.delete(key);
        stringRedisTemplate.opsForSet().remove(VIEW_KEY_SET, key);
    }

    private void refreshKeyTtl(String key) {
        stringRedisTemplate.expire(key, VIEW_KEY_TTL);
        stringRedisTemplate.expire(VIEW_KEY_SET, VIEW_KEY_TTL);
    }

    public void evict(Long suggestionId) {
        removeKey(VIEW_KEY_PREFIX + suggestionId);
    }
}
