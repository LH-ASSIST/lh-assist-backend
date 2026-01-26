package com.lh.assist.common.security.jwt;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class TokenBlacklistService {

	private static final String ACCESS_BLACKLIST_PREFIX = "auth:blacklist:access:";

	private final StringRedisTemplate stringRedisTemplate;

	public TokenBlacklistService(StringRedisTemplate stringRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
	}

	/**
	 * 액세스 토큰 jti를 블랙리스트에 등록한다
	 *
	 * @param jti 액세스 토큰 식별자
	 * @param ttl 액세스 토큰 남은 만료 시간
	 */
	public void blacklistAccessToken(String jti, Duration ttl) {
		if (jti == null || ttl.isZero() || ttl.isNegative()) {
			return;
		}
		// access jti를 만료 시점까지 저장해 즉시 로그아웃을 반영한다.
		stringRedisTemplate.opsForValue().set(ACCESS_BLACKLIST_PREFIX + jti, "1", ttl);
	}

	/**
	 * 액세스 토큰 jti가 블랙리스트에 등록되어 있는지 확인한다
	 *
	 * @param jti 액세스 토큰 식별자
	 * @return 블랙리스트 여부
	 */
	public boolean isBlacklisted(String jti) {
		if (jti == null) {
			return false;
		}
		return Boolean.TRUE.equals(stringRedisTemplate.hasKey(ACCESS_BLACKLIST_PREFIX + jti));
	}
}
