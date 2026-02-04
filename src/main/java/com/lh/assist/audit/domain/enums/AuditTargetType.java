package com.lh.assist.audit.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuditTargetType {
    DOCUMENT("문서"),
    ANALYSIS_RESULT("분석 결과"),
    ANALYSIS_JOB("분석 작업"),
    DOCUMENT_APPROVAL("문서 승인"),
    NOTICE("공지"),
    SUGGESTION("건의"),
    USER("사용자");

    private final String description;
}