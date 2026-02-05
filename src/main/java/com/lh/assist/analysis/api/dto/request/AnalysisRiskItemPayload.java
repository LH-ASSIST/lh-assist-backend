package com.lh.assist.analysis.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lh.assist.analysis.domain.enums.AnalysisRiskType;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalysisRiskItemPayload {

    @JsonProperty("risk_type")
    private AnalysisRiskType riskType;

    @JsonProperty("detected_text")
    private String detectedText;

    @JsonProperty("guide_message")
    private String guideMessage;

    private Integer priority;

    @JsonProperty("similar_case_content")
    private String similarCaseContent;

    private String reasoning;

    private List<AnalysisEvidencePayload> evidences;
}