package com.lh.assist.common.security.jwt;

import com.lh.assist.user.domain.entity.User;
import com.lh.assist.common.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
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

	private final SecretKey key;
	private final long expirationMillis;

	public JwtTokenProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-token-expiration}") long expirationMillis
	) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMillis = expirationMillis;
	}

	public String createToken(User user) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMillis);

		return Jwts.builder()
				.subject(String.valueOf(user.getUserId()))
				.claim("userId", user.getUserId())
				.claim("email", user.getEmail())
				.claim("role", user.getRole().name())
				.issuedAt(now)
				.expiration(expiry)
				.signWith(key)
				.compact();
	}

	public Authentication getAuthentication(String token) {
		Claims claims = parseClaims(token);
		String role = claims.get("role", String.class);
		List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
		String email = claims.get("email", String.class);
		Number userIdValue = claims.get("userId", Number.class);
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

	private Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}