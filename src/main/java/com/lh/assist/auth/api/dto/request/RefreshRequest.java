package com.lh.assist.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
		@NotBlank
		String refreshToken
) {
}