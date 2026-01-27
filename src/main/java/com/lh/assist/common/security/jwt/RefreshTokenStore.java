package com.lh.assist.common.security.jwt;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenStore {

	private static final String TOKEN_KEY_PREFIX = "auth:refresh:token:";
	private static final String FAMILY_SET_PREFIX = "auth:refresh:family:";
	private static final DefaultRedisScript<Long> CONSUME_TOKEN_SCRIPT = createConsumeTokenScript();

	private final StringRedisTemplate stringRedisTemplate;

	public RefreshTokenStore(StringRedisTemplate stringRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
	}

	/**
	 * 리프레시 토큰 jti를 Redis에 저장하고 family 체인을 관리한다
	 *
	 * @param jti 리프레시 토큰 식별자
	 * @param familyId 리프레시 토큰 family 식별자
	 * @param userId 사용자 ID
	 * @param ttl 리프레시 토큰 만료 TTL
	 */
	public void storeToken(String jti, String familyId, Long userId, Duration ttl) {
		String tokenKey = tokenKey(jti);
		stringRedisTemplate.opsForValue().set(tokenKey, String.valueOf(userId), ttl);
		String familyKey = familyKey(familyId);
		stringRedisTemplate.opsForSet().add(familyKey, jti);
		stringRedisTemplate.expire(familyKey, ttl);
	}

	/**
	 * 리프레시 토큰 jti가 Redis에 존재하는지 확인한다
	 *
	 * @param jti 리프레시 토큰 식별자
	 * @return 존재 여부
	 */
	public boolean exists(String jti) {
		return Boolean.TRUE.equals(stringRedisTemplate.hasKey(tokenKey(jti)));
	}

	/**
	 * 리프레시 토큰 jti를 Redis에서 제거한다
	 *
	 * @param jti 리프레시 토큰 식별자
	 * @param familyId 리프레시 토큰 family 식별자
	 */
	public void removeToken(String jti, String familyId) {
		stringRedisTemplate.delete(tokenKey(jti));
		stringRedisTemplate.opsForSet().remove(familyKey(familyId), jti);
	}

	/**
	 * 리프레시 토큰 jti를 Redis에서 원자적으로 소비한다
	 *
	 * @param jti 리프레시 토큰 식별자
	 * @param familyId 리프레시 토큰 family 식별자
	 * @return 소비 성공 여부
	 */
	public boolean consumeToken(String jti, String familyId) {
		Long result = stringRedisTemplate.execute(
				CONSUME_TOKEN_SCRIPT,
				List.of(tokenKey(jti), familyKey(familyId)),
				jti
		);
		return Long.valueOf(1L).equals(result);
	}

	/**
	 * 리프레시 토큰 family에 속한 모든 jti를 조회한다
	 *
	 * @param familyId 리프레시 토큰 family 식별자
	 * @return family에 속한 jti 목록
	 */
	public Set<String> getFamilyTokens(String familyId) {
		Set<String> tokens = stringRedisTemplate.opsForSet().members(familyKey(familyId));
		return tokens != null ? tokens : Collections.emptySet();
	}

	/**
	 * 리프레시 토큰 family 전체를 폐기한다
	 *
	 * @param familyId 리프레시 토큰 family 식별자
	 */
	public void revokeFamily(String familyId) {
		Set<String> tokens = getFamilyTokens(familyId);
		for (String jti : tokens) {
			stringRedisTemplate.delete(tokenKey(jti));
		}
		stringRedisTemplate.delete(familyKey(familyId));
	}

	private String tokenKey(String jti) {
		return TOKEN_KEY_PREFIX + jti;
	}

	private String familyKey(String familyId) {
		return FAMILY_SET_PREFIX + familyId;
	}

	private static DefaultRedisScript<Long> createConsumeTokenScript() {
		DefaultRedisScript<Long> script = new DefaultRedisScript<>();
		script.setResultType(Long.class);
		script.setScriptText(
				"if redis.call('EXISTS', KEYS[1]) == 1 then " +
				"redis.call('DEL', KEYS[1]); " +
				"redis.call('SREM', KEYS[2], ARGV[1]); " +
				"return 1; " +
				"end; " +
				"return 0;"
		);
		return script;
	}
}
