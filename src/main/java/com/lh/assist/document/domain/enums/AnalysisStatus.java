package com.lh.assist.document.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AnalysisStatus {
    PENDING("분석 대기"),
    ANALYZING("분석 중"),
    COMPLETED("분석 완료"),
    FAILED("분석 실패");

    private final String description;
}