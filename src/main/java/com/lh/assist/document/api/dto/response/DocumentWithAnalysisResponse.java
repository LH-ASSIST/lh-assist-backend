package com.lh.assist.document.api.dto.response;

import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.document.domain.enums.AnalysisStatus;
import com.lh.assist.document.domain.enums.ApprovalStatus;
import com.lh.assist.document.domain.enums.DocumentType;
import com.lh.assist.document.domain.enums.MetadataStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "문서 + 분석 요약 목록 응답")
public class DocumentWithAnalysisResponse {
    @Schema(description = "문서 ID", example = "1")
    private final Long docId;
    @Schema(description = "문서 제목", example = "사업계획서_2024.pdf")
    private final String title;
    @Schema(description = "문서 유형", example = "BUSINESS_PLAN")
    private final DocumentType docType;
    @Schema(description = "기준일자", example = "2026-01-31")
    private final LocalDate baseDate;
    @Schema(description = "분석 상태", example = "ANALYZING")
    private final AnalysisStatus analysisStatus;
    @Schema(description = "승인 상태", example = "WAITING")
    private final ApprovalStatus approvalStatus;
    @Schema(description = "메타데이터 상태", example = "PENDING")
    private final MetadataStatus metadataStatus;
    @Schema(description = "접근 유형 (OWNER/APPROVER)", example = "OWNER")
    private final DocumentAccessType accessType;
    @Schema(description = "분석 결과 ID", example = "10")
    private final Long analysisId;
    @Schema(description = "분석 결과 상태", example = "SUCCEEDED")
    private final AnalysisResultStatus analysisResultStatus;
    @Schema(description = "총 리스크 점수", example = "42")
    private final Integer totalRiskScore;
    @Schema(description = "생성 일시", example = "2026-01-19T10:22:11")
    private final LocalDateTime createdAt;
}