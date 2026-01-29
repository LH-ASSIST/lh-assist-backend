package com.lh.assist.auth.api.dto.request;

import com.lh.assist.auth.domain.enums.EmailVerificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
	@Email
	@NotBlank
	String email,
	@NotNull
	EmailVerificationPurpose purpose,
	@NotBlank
	@Pattern(regexp = "^[0-9]{6}$")
	String code
) {
}