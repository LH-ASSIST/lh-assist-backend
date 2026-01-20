package com.lh.assist.auth.api.dto;

import com.lh.assist.user.domain.UserDepartment;
import com.lh.assist.user.domain.UserPosition;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class SignupRequest {
	@NotBlank
	@Email
	private String email;

	@NotBlank
	private String password;

	@NotBlank
	private String name;

	@NotNull
	private UserDepartment department;

	@NotNull
	private UserPosition position;

}
