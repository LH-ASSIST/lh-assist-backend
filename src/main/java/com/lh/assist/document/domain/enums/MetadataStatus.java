package com.lh.assist.document.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MetadataStatus {
	PENDING("처리 대기"),
	COMPLETED("처리 완료"),
	FAILED("처리 실패");

	private final String description;
}