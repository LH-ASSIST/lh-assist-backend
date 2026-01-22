package com.lh.assist.regulation.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RegulationType {
	LAW("법률"),
	ENFORCEMENT_DECREE("시행령"),
	ENFORCEMENT_RULE("시행규칙");

	private final String description;
}