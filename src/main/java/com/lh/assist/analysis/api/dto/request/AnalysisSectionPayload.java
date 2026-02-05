package com.lh.assist.analysis.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalysisSectionPayload {

    @JsonProperty("external_section_id")
    private String externalSectionId;

    @JsonProperty("page_number")
    private Integer pageNumber;

    private List<Double> bbox;

    @JsonProperty("is_violation")
    private Boolean isViolation;

    @JsonProperty("risk_score")
    private Integer riskScore;

    private String reasoning;

    @JsonProperty("risk_items")
    private List<AnalysisRiskItemPayload> riskItems;
}