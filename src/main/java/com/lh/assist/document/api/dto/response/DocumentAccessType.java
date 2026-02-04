package com.lh.assist.document.api.dto.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentAccessType {
	OWNER("소유자"),
	APPROVER("문서 승인 담당자");

	private final String description;
}