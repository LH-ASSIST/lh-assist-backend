package com.lh.assist.user.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserPosition {
	CHIEF("처장"),
	TEAM_LEAD("팀장"),
	DEPUTY_MANAGER("차장"),
	MANAGER("과장"),
	STAFF("사원");

	private final String description;
}