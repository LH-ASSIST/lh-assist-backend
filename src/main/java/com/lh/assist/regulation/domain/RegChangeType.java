package com.lh.assist.regulation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RegChangeType {
	NEW("제정"),
	AMENDED("개정"),
	REPEALED("폐지");

	private final String description;
}