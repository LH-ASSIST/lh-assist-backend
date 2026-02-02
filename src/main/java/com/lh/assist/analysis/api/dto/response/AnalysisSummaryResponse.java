package com.lh.assist.analysis.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisSummaryResponse {
    private final Long analysisId;
    private final Integer totalRiskScore;
    private final String riskLevel;
    private final int totalViolations;
}