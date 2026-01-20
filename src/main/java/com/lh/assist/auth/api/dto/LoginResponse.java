package com.lh.assist.auth.api.dto;

import com.lh.assist.user.domain.UserRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
	private final String accessToken;
	private final String tokenType;
	private final Long userId;
	private final String email;
	private final UserRole role;
}
