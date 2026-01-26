package com.lh.assist.auth.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RefreshResponse {
	private final String accessToken;
	private final String refreshToken;
	private final String tokenType;
}