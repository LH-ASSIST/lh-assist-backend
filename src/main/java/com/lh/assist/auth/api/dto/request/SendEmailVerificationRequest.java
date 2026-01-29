package com.lh.assist.auth.api.dto.request;

import com.lh.assist.auth.domain.enums.EmailVerificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendEmailVerificationRequest(
	@Email
	@NotBlank
	String email,
	@NotNull
	EmailVerificationPurpose purpose
) {
}