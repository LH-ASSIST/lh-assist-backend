package com.lh.assist.common.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtTokenProvider tokenProvider;
	private final TokenBlacklistService tokenBlacklistService;

	public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, TokenBlacklistService tokenBlacklistService) {
		this.tokenProvider = tokenProvider;
		this.tokenBlacklistService = tokenBlacklistService;
	}

	@Override
	protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain
	)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith("Bearer ")) {
			String token = header.substring(7);
			if (tokenProvider.validateToken(token)) {
				var claims = tokenProvider.parseClaims(token);
				if (tokenProvider.isAccessToken(claims) && !tokenBlacklistService.isBlacklisted(claims.getId())) {
					Authentication authentication = tokenProvider.createAuthentication(claims, token);
					SecurityContextHolder.getContext().setAuthentication(authentication);
				}
			}
		}
		filterChain.doFilter(request, response);
	}
}