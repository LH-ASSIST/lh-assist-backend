package com.lh.assist.user.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserDepartment {
	PUBLIC_HOUSING_HEADQUARTERS("공공주택본부"),
	HOUSING_INNOVATION_OFFICE("주거혁신처"),
	DESIGN_VERIFICATION_OFFICE("설계검증처"),
	PUBLIC_HOUSING_BUSINESS_OFFICE("공공주택사업처"),
	PRIVATE_COOPERATION_OFFICE("민간협력사업처"),
	PUBLIC_HOUSING_FACILITIES_OFFICE("공공주택설비처"),
	PUBLIC_HOUSING_ELECTRICAL_OFFICE("공공주택전기처"),
	RESIDENTIAL_ENVIRONMENT_PLANNING_GROUP("주거환경계획단"),
	YOUTH_HOUSING_TASKFORCE("청년주택추진단(TFT)"),
	ETC("기타");

	private final String description;
}