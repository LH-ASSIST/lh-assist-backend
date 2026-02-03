package com.lh.assist.analysis.api.dto.request;

import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "분석 완료 콜백 요청")
public class AnalysisCallbackRequest {
    @NotNull
    @Schema(description = "분석 결과 상태", example = "SUCCEEDED")
    private AnalysisResultStatus status;

    @Schema(description = "총 리스크 점수", example = "42")
    private Integer totalRiskScore;

    @Schema(description = "실패 사유", example = "분석 서버 오류")
    private String failReason;
}