package com.lh.assist.document.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentType {
    PLAN("사업계획서"),
    NOTICE("입주자 모집 공고문"),
    CONTRACT("공사/용역 계약서"),
    ETC("기타 문서");

    private final String description;
}