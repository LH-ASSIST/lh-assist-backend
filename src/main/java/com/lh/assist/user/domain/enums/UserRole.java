package com.lh.assist.user.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {
	ADMIN("관리자"),
	USER("사용자");

	private final String description;

	public static boolean isAdmin(String role) {
		return ADMIN.name().equals(role);
	}
}