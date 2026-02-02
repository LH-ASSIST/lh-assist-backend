package com.lh.assist.analysis.api.dto.response;

import com.lh.assist.analysis.domain.enums.AnalysisRiskType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisRiskItemResponse {
    private final Long riskId;
    private final AnalysisRiskType riskType;
    private final String detectedText;
    private final String guideMessage;
    private final Integer priority;
    private final String similarCaseContent;
    private final String reasoning;
}