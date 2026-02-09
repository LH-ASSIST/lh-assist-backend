package com.lh.assist.analysis.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "대시보드 분석 요약 응답")
public class AnalysisDashboardResponse {
    @Schema(description = "이번 달 분석 요청 수", example = "12")
    private final long monthlyReviewCount;
    @Schema(description = "이번 달 고위험 문서 수", example = "3")
    private final long highRiskDocumentCount;
    @Schema(description = "이번 달 평균 안전 점수", example = "74")
    private final int averageSafetyScore;
    @Schema(description = "이번 달 저위험 문서 수", example = "5")
    private final long lowRiskDocumentCount;
}