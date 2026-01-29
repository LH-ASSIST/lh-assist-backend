package com.lh.assist.auth.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmailVerificationPurpose {
	SIGNUP("회원가입"),
	PASSWORD_RESET("패스워드 초기화"),
	EMAIL_CHANGE("이메일 변경");

	private final String description;
}