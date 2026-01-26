package com.lh.assist.common.security.jwt;

import com.lh.assist.common.exception.AuthException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

	private final JwtTokenProvider tokenProvider;
	private final RefreshTokenStore refreshTokenStore;
	private final TokenBlacklistService tokenBlacklistService;
	private final UserRepository userRepository;
	private final Duration refreshTokenTtl;

	public TokenService(
			JwtTokenProvider tokenProvider,
			RefreshTokenStore refreshTokenStore,
			TokenBlacklistService tokenBlacklistService,
			UserRepository userRepository,
			@Value("${jwt.refresh-token-expiration}") long refreshTokenExpirationMillis
	) {
		this.tokenProvider = tokenProvider;
		this.refreshTokenStore = refreshTokenStore;
		this.tokenBlacklistService = tokenBlacklistService;
		this.userRepository = userRepository;
		this.refreshTokenTtl = Duration.ofMillis(refreshTokenExpirationMillis);
	}

	/**
	 * 로그인 시 새 액세스/리프레시 토큰을 발급한다
	 *
	 * @param user 로그인 사용자
	 * @return 액세스/리프레시 토큰 쌍
	 */
	public TokenPair issueLoginTokens(User user) {
		// 로그인 시 새로운 refresh family 체인을 시작한다.
		String familyId = UUID.randomUUID().toString();
		return issueTokens(user, familyId);
	}

	/**
	 * 리프레시 토큰으로 액세스 토큰을 재발급한다
	 *
	 * 재사용된 리프레시 토큰이면 family 전체를 폐기한다
	 *
	 * @param refreshToken 리프레시 토큰
	 * @return 액세스/리프레시 토큰 쌍
	 */
	public TokenPair rotateRefreshToken(String refreshToken) {
		// refresh 토큰 회전; 재사용 탐지 시 family 전체 폐기.
		Claims claims = parseClaimsOrUnauthorized(refreshToken);
		if (!tokenProvider.isRefreshToken(claims)) {
			throw new AuthException(ErrorCode.UNAUTHORIZED);
		}

		String jti = claims.getId();
		String familyId = tokenProvider.getFamilyId(claims);
		if (jti == null || familyId == null) {
			throw new AuthException(ErrorCode.UNAUTHORIZED);
		}

		if (!refreshTokenStore.exists(jti)) {
			refreshTokenStore.revokeFamily(familyId);
			throw new AuthException(ErrorCode.UNAUTHORIZED);
		}

		Long userId = getUserId(claims);
		if (userId == null) {
			throw new AuthException(ErrorCode.UNAUTHORIZED);
		}
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new AuthException(ErrorCode.UNAUTHORIZED));

		refreshTokenStore.removeToken(jti, familyId);
		return issueTokens(user, familyId);
	}

	/**
	 * 로그아웃 처리로 리프레시 토큰 family를 폐기하고 액세스 토큰을 블랙리스트 처리한다
	 *
	 * @param refreshToken 리프레시 토큰
	 * @param accessToken 액세스 토큰
	 */
	public void logout(
			String refreshToken,
			String accessToken
	) {
		revokeRefreshFamilyIfPresent(refreshToken);
		blacklistAccessIfPresent(accessToken);
	}

	/**
	 * 액세스/리프레시 토큰을 발급하고 리프레시 토큰을 Redis에 저장한다
	 *
	 * @param user 사용자
	 * @param familyId 리프레시 토큰 family 식별자
	 * @return 액세스/리프레시 토큰 쌍
	 */
	private TokenPair issueTokens(
			User user,
			String familyId
	) {
		String accessToken = tokenProvider.createAccessToken(user);
		String refreshToken = tokenProvider.createRefreshToken(user, familyId);
		Claims refreshClaims = tokenProvider.parseClaims(refreshToken);
		String refreshJti = refreshClaims.getId();
		refreshTokenStore.storeToken(refreshJti, familyId, user.getUserId(), refreshTokenTtl);
		return new TokenPair(accessToken, refreshToken);
	}

	private Claims parseClaimsOrUnauthorized(String token) {
		try {
			return tokenProvider.parseClaims(token);
		} catch (RuntimeException ex) {
			throw new AuthException(ErrorCode.UNAUTHORIZED);
		}
	}

	private void revokeRefreshFamilyIfPresent(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return;
		}
		Claims claims = parseClaimsOrUnauthorized(refreshToken);
		if (!tokenProvider.isRefreshToken(claims)) {
			throw new AuthException(ErrorCode.UNAUTHORIZED);
		}
		String familyId = tokenProvider.getFamilyId(claims);
		if (familyId != null) {
			refreshTokenStore.revokeFamily(familyId);
		}
	}

	private void blacklistAccessIfPresent(String accessToken) {
		if (accessToken == null || accessToken.isBlank()) {
			return;
		}
		try {
			Claims claims = tokenProvider.parseClaims(accessToken);
			if (tokenProvider.isAccessToken(claims)) {
				String jti = claims.getId();
				Date expiration = claims.getExpiration();
				Duration ttl = expiration != null
						? Duration.between(Instant.now(), expiration.toInstant())
						: Duration.ZERO;
				tokenBlacklistService.blacklistAccessToken(jti, ttl);
			}
		} catch (RuntimeException ex) {
			// 로그아웃 시 잘못된 access 토큰은 무시한다.
		}
	}

	private Long getUserId(Claims claims) {
		Number userIdValue = claims.get("userId", Number.class);
		if (userIdValue != null) {
			return userIdValue.longValue();
		}
		String subject = claims.getSubject();
		return subject != null ? Long.parseLong(subject) : null;
	}
}
