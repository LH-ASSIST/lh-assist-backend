package com.lh.assist.domain.user;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {
	ADMIN("관리자"),
	USER("사용자");

	private final String description;
}