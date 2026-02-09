package com.lh.assist.analysis.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AnalysisRiskType {
    MISSING("누락", "missing", "#E53935"),
    APPROPRIATENESS("적정성", "appropriateness", "#FB8C00"),
    CLARITY("명확성", "clarity", "#7CB342"),
    PROCEDURE_COMPLIANCE("절차준수", "procedure", "#1E88E5");

    private final String description;
    private final String cssClass;
    private final String colorCode;
}