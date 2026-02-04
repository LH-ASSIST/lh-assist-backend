package com.lh.assist.analysis.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AnalysisEvidenceSourceType {
    REG_ITEM("규정 조항"),
    AUDIT_MANUAL_ITEM("감사 매뉴얼 조항"),
    AUDIT_ITEM("유사 사례");

    private final String description;
}