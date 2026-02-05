package com.lh.assist.analysis.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "분석 완료 콜백 요청")
public class AnalysisCallbackRequest {
    @Schema(description = "분석 결과 상태", example = "SUCCEEDED")
    private AnalysisResultStatus status;

    @Schema(description = "총 리스크 점수", example = "42")
    @JsonProperty("total_risk_score")
    private Integer totalRiskScore;

    @Schema(description = "실패 사유", example = "분석 서버 오류")
    @JsonProperty("fail_reason")
    private String failReason;

    @JsonProperty("analysis_result")
    private AnalysisResultPayload analysisResult;

    @Schema(description = "파싱 JSON S3 키", example = "parsed/123/uuid-file.json.gz")
    @JsonProperty("parsed_json_s3_key")
    private String parsedJsonS3Key;

    @JsonProperty("sections")
    private List<AnalysisSectionPayload> sections;

    public AnalysisResultStatus resolveStatus() {
        if (status != null) {
            return status;
        }
        return analysisResult != null ? analysisResult.getStatus() : null;
    }

    public Integer resolveTotalRiskScore() {
        if (totalRiskScore != null) {
            return totalRiskScore;
        }
        return analysisResult != null ? analysisResult.getTotalRiskScore() : null;
    }

    public String resolveParsedJsonS3Key() {
        if (parsedJsonS3Key != null && !parsedJsonS3Key.isBlank()) {
            return parsedJsonS3Key;
        }
        return null;
    }
}