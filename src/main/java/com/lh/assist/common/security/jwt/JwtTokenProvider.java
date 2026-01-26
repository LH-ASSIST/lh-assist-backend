package com.lh.assist.common.security.jwt;

import com.lh.assist.user.domain.entity.User;
import com.lh.assist.common.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import java.util.UUID;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

	private static final String CLAIM_TYPE = "type";
	private static final String CLAIM_FAMILY = "family";
	private static final String CLAIM_USER_ID = "userId";
	private static final String CLAIM_EMAIL = "email";
	private static final String CLAIM_ROLE = "role";
	private static final String TYPE_ACCESS = "access";
	private static final String TYPE_REFRESH = "refresh";

	private final SecretKey key;
	private final long accessTokenExpirationMillis;
	private final long refreshTokenExpirationMillis;
	private final String issuer;
	private final String audience;

	public JwtTokenProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-token-expiration}") long accessTokenExpirationMillis,
			@Value("${jwt.refresh-token-expiration}") long refreshTokenExpirationMillis,
			@Value("${jwt.issuer:lh-assist}") String issuer,
			@Value("${jwt.audience:lh-assist-client}") String audience
	) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenExpirationMillis = accessTokenExpirationMillis;
		this.refreshTokenExpirationMillis = refreshTokenExpirationMillis;
		this.issuer = issuer;
		this.audience = audience;
	}

	public String createAccessToken(User user) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + accessTokenExpirationMillis);
		String jti = UUID.randomUUID().toString();

		return Jwts.builder()
				.subject(String.valueOf(user.getUserId()))
				.claim(CLAIM_USER_ID, user.getUserId())
				.claim(CLAIM_EMAIL, user.getEmail())
				.claim(CLAIM_ROLE, user.getRole().name())
				.claim(CLAIM_TYPE, TYPE_ACCESS)
				.issuedAt(now)
				.expiration(expiry)
				.id(jti)
				.issuer(issuer)
				.audience().add(audience).and()
				.signWith(key)
				.compact();
	}

	public String createRefreshToken(User user, String familyId) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + refreshTokenExpirationMillis);
		String jti = UUID.randomUUID().toString();

		return Jwts.builder()
				.subject(String.valueOf(user.getUserId()))
				.claim(CLAIM_USER_ID, user.getUserId())
				.claim(CLAIM_EMAIL, user.getEmail())
				.claim(CLAIM_ROLE, user.getRole().name())
				.claim(CLAIM_TYPE, TYPE_REFRESH)
				.claim(CLAIM_FAMILY, familyId)
				.issuedAt(now)
				.expiration(expiry)
				.id(jti)
				.issuer(issuer)
				.audience().add(audience).and()
				.signWith(key)
				.compact();
	}

	public Authentication getAuthentication(String token) {
		Claims claims = parseClaims(token);
		if (!isAccessToken(claims)) {
			throw new IllegalArgumentException("Not an access token");
		}
		return createAuthentication(claims, token);
	}

	public Authentication createAuthentication(Claims claims, String token) {
		String role = claims.get(CLAIM_ROLE, String.class);
		List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
		String email = claims.get(CLAIM_EMAIL, String.class);
		Number userIdValue = claims.get(CLAIM_USER_ID, Number.class);
		Long userId = userIdValue != null ? userIdValue.longValue() : null;
		if (userId == null && claims.getSubject() != null) {
			userId = Long.parseLong(claims.getSubject());
		}
		UserPrincipal principal = new UserPrincipal(userId, email, role);
		return new UsernamePasswordAuthenticationToken(principal, token, authorities);
	}

	public boolean validateToken(String token) {
		try {
			parseClaims(token);
			return true;
		} catch (RuntimeException ex) {
			return false;
		}
	}

	public Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(key)
				.requireIssuer(issuer)
				.requireAudience(audience)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	public boolean isAccessToken(Claims claims) {
		return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
	}

	public boolean isRefreshToken(Claims claims) {
		return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
	}

	public String getFamilyId(Claims claims) {
		return claims.get(CLAIM_FAMILY, String.class);
	}
}
