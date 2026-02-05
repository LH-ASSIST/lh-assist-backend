package com.lh.assist.analysis.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalysisResultPayload {

    @JsonProperty("document_id")
    private String documentId;

    private AnalysisResultStatus status;

    @JsonProperty("total_risk_score")
    private Integer totalRiskScore;
}