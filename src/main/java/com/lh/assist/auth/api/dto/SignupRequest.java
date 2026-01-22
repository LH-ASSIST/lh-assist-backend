package com.lh.assist.auth.api.dto;

import com.lh.assist.user.domain.UserDepartment;
import com.lh.assist.user.domain.UserPosition;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SignupRequest(
		@NotBlank
		@Email
		String email,
		@NotBlank
		String password,
		@NotBlank
		String name,
		@NotNull
		UserDepartment department,
		@NotNull
		UserPosition position
) {
}
