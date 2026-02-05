package com.lh.assist.analysis.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lh.assist.analysis.domain.enums.AnalysisEvidenceSourceType;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalysisEvidencePayload {

    @JsonProperty("source_type")
    private AnalysisEvidenceSourceType sourceType;

    @JsonProperty("source_id")
    private String sourceId;

    private String quote;
}