package com.lh.assist.analysis.api.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class AnalysisSectionResponse {
    private final Long sectionId;
    private final Integer page;
    private final String bbox;
    private final boolean isViolation;
    private final Integer riskScore;
    private final String reasoning;
    private final List<AnalysisRiskItemResponse> riskItems;
}