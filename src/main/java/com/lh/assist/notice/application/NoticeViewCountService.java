package com.lh.assist.notice.application;

import com.lh.assist.notice.domain.repository.NoticeRepository;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeViewCountService {

    private static final String VIEW_KEY_PREFIX = "notice:view:";
    private static final String VIEW_KEY_SET = "notice:view:keys";
    private static final Duration VIEW_KEY_TTL = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;
    private final NoticeRepository noticeRepository;

    public void increment(Long noticeId) {
        String key = VIEW_KEY_PREFIX + noticeId;
        stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.opsForSet().add(VIEW_KEY_SET, key);
        refreshKeyTtl(key);
    }

    public int getViewCount(Long noticeId, int baseCount) {
        String key = VIEW_KEY_PREFIX + noticeId;
        String value = stringRedisTemplate.opsForValue().get(key);
        int delta = parseDelta(value);
        return baseCount + Math.max(delta, 0);
    }

    public Map<Long, Integer> getViewCountDeltas(List<Long> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> keys = noticeIds.stream()
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
                deltas.put(noticeIds.get(i), delta);
            }
        }
        return deltas;
    }

    @Scheduled(fixedDelayString = "${app.notice.view-count.flush-interval-ms:60000}")
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
                log.warn("공지사항 조회수 반영에 실패했습니다. key={}, delta={}", key, delta, ex);
            }
        }
    }

    private void flushKey(String key, int delta) {
        Long noticeId = parseNoticeId(key);
        if (noticeId == null) {
            stringRedisTemplate.opsForSet().remove(VIEW_KEY_SET, key);
            return;
        }
        int updated = noticeRepository.incrementViewCount(noticeId, delta);
        if (updated == 0) {
            removeKey(key);
            log.warn("삭제된 공지사항으로 조회수 반영이 중단되었습니다. key={}", key);
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

    private Long parseNoticeId(String key) {
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
        refreshKeyTtl(key);
    }

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

    public void evict(Long noticeId) {
        removeKey(VIEW_KEY_PREFIX + noticeId);
    }
}