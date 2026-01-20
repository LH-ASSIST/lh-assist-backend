package com.lh.assist.auth.api.dto;

import com.lh.assist.user.domain.UserDepartment;
import com.lh.assist.user.domain.UserPosition;
import com.lh.assist.user.domain.UserStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SignupResponse {
	private final Long userId;
	private final String email;
	private final String name;
	private final UserDepartment department;
	private final UserPosition position;
	private final UserStatus status;
	private final LocalDateTime createdAt;
}