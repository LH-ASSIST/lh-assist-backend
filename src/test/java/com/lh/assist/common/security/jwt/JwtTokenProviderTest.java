package com.lh.assist.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserPosition;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

	private static final String SECRET = "test-jwt-secret-test-jwt-secret-test-jwt-secret";
	private static final String ISSUER = "lh-assist";
	private static final String AUDIENCE = "lh-assist-client";
	private static final long ACCESS_TTL_MS = 60_000L;
	private static final long REFRESH_TTL_MS = 120_000L;

	@Test
	@DisplayName("액세스 토큰에는 필수 클레임이 포함되어야 한다")
	void accessToken_hasRequiredClaims() {
		JwtTokenProvider provider = new JwtTokenProvider(SECRET, ACCESS_TTL_MS, REFRESH_TTL_MS, ISSUER, AUDIENCE);
		User user = user();

		String token = provider.createAccessToken(user);
		Claims claims = provider.parseClaims(token);

		assertThat(provider.isAccessToken(claims)).isTrue();
		assertThat(claims.getId()).isNotBlank();
		assertThat(claims.getIssuer()).isEqualTo(ISSUER);
		assertThat(audienceContains(claims)).isTrue();
		assertThat(provider.getFamilyId(claims)).isNull();
	}

	@Test
	@DisplayName("리프레시 토큰에는 family 클레임이 포함되어야 한다")
	void refreshToken_hasFamilyClaim() {
		JwtTokenProvider provider = new JwtTokenProvider(SECRET, ACCESS_TTL_MS, REFRESH_TTL_MS, ISSUER, AUDIENCE);
		User user = user();

		String token = provider.createRefreshToken(user, "family-1");
		Claims claims = provider.parseClaims(token);

		assertThat(provider.isRefreshToken(claims)).isTrue();
		assertThat(provider.getFamilyId(claims)).isEqualTo("family-1");
	}

	@Test
	@DisplayName("리프레시 토큰으로는 인증 객체를 만들 수 없다")
	void refreshToken_cannotAuthenticate() {
		JwtTokenProvider provider = new JwtTokenProvider(SECRET, ACCESS_TTL_MS, REFRESH_TTL_MS, ISSUER, AUDIENCE);
		User user = user();

		String token = provider.createRefreshToken(user, "family-1");

		assertThatThrownBy(() -> provider.getAuthentication(token))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private User user() {
		return User.builder()
				.userId(1L)
				.email("tester@lh.com")
				.password("hashed")
				.name("Tester")
				.department(UserDepartment.ETC)
				.position(UserPosition.ETC)
				.role(UserRole.USER)
				.status(UserStatus.ACTIVE)
				.emailVerified(true)
				.attemptCount(0)
				.build();
	}

	private boolean audienceContains(Claims claims) {
		Object aud = claims.get("aud");
		if (aud instanceof String audience) {
			return AUDIENCE.equals(audience);
		}
		if (aud instanceof java.util.Collection<?> collection) {
			return collection.contains(AUDIENCE);
		}
		return AUDIENCE.equals(claims.getAudience());
	}
}
