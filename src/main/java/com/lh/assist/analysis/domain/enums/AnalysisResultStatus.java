package com.lh.assist.analysis.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AnalysisResultStatus {
	REQUESTED("요청됨"),
	RUNNING("실행 중"),
	SUCCEEDED("성공"),
	FAILED("실패");

	private final String description;
}