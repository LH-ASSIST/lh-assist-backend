package com.lh.assist.user.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {
	ACTIVE("활성"),
	PENDING("대기"),
	SUSPENDED("정지");

	private final String description;
}