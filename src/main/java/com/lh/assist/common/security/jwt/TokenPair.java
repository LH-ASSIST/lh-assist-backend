package com.lh.assist.common.security.jwt;

public record TokenPair(
		String accessToken,
		String refreshToken
) {
}