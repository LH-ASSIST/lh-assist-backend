package com.lh.assist.common.viewcount;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public abstract class AbstractViewCountService {

    private static final Duration DEFAULT_VIEW_KEY_TTL = Duration.ofDays(7);

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final StringRedisTemplate stringRedisTemplate;

    protected AbstractViewCountService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    protected abstract String viewKeyPrefix();

    protected abstract String viewKeySet();

    protected abstract int incrementViewCount(Long id, int delta);

    protected abstract String flushFailLogMessage();

    protected abstract String deletedLogMessage();

    protected Duration viewKeyTtl() {
        return DEFAULT_VIEW_KEY_TTL;
    }

    /**
     * Redis에 조회수 증가를 기록한다
     *
     * @param id 조회 대상 ID
     */
    public void increment(Long id) {
        String key = viewKeyPrefix() + id;
        stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.opsForSet().add(viewKeySet(), key);
        refreshKeyTtl(key);
    }

    public int getViewCount(
            Long id,
            int baseCount
    ) {
        String key = viewKeyPrefix() + id;
        String value = stringRedisTemplate.opsForValue().get(key);
        int delta = parseDelta(value);
        return baseCount + Math.max(delta, 0);
    }

    public Map<Long, Integer> getViewCountDeltas(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> keys = ids.stream()
                .map(id -> viewKeyPrefix() + id)
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
                deltas.put(ids.get(i), delta);
            }
        }
        return deltas;
    }

    /**
     * Redis에 누적된 조회수를 주기적으로 DB에 반영한다
     *
     * 반영 실패 시 해당 키의 증가분을 복구한다
     */
    protected void flushToDatabaseInternal() {
        Set<String> keys = stringRedisTemplate.opsForSet().members(viewKeySet());
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
                if (log.isWarnEnabled()) {
                    log.warn(flushFailLogMessage(), key, delta, ex);
                }
            }
        }
    }

    /**
     * 특정 키의 증가분을 DB에 반영한다
     *
     * @param key 조회수 키
     * @param delta 반영할 증가분
     */
    private void flushKey(
            String key,
            int delta
    ) {
        Long id = parseId(key);
        if (id == null) {
            stringRedisTemplate.opsForSet().remove(viewKeySet(), key);
            return;
        }
        int updated = incrementViewCount(id, delta);
        if (updated == 0) {
            removeKey(key);
            if (log.isWarnEnabled()) {
                log.warn(deletedLogMessage(), key);
            }
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
     * 조회수 키에서 ID를 파싱한다
     *
     * @param key 조회수 키
     * @return 파싱된 ID
     */
    private Long parseId(String key) {
        if (key == null || !key.startsWith(viewKeyPrefix())) {
            return null;
        }
        String idPart = key.substring(viewKeyPrefix().length());
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
    private void restoreDelta(
            String key,
            int delta
    ) {
        stringRedisTemplate.opsForValue().increment(key, delta);
        stringRedisTemplate.opsForSet().add(viewKeySet(), key);
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
        stringRedisTemplate.opsForSet().remove(viewKeySet(), key);
    }

    private void refreshKeyTtl(String key) {
        Duration ttl = viewKeyTtl();
        stringRedisTemplate.expire(key, ttl);
        stringRedisTemplate.expire(viewKeySet(), ttl);
    }

    public void evict(Long id) {
        removeKey(viewKeyPrefix() + id);
    }
}
