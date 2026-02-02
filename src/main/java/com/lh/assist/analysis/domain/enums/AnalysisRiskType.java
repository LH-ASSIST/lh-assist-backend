package com.lh.assist.analysis.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AnalysisRiskType {
    MISSING("누락"),
    APPROPRIATENESS("적정성"),
    CLARITY("명확성"),
    PROCEDURE_COMPLIANCE("절차준수");

    private final String description;
}