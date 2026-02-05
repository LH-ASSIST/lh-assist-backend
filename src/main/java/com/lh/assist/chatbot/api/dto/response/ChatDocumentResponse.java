package com.lh.assist.chatbot.api.dto.response;

import com.lh.assist.document.domain.enums.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "챗봇 문서 선택 목록 응답")
public class ChatDocumentResponse {
    @Schema(description = "문서 ID", example = "1")
    private final Long docId;
    @Schema(description = "문서 제목", example = "사업계획서_2024.pdf")
    private final String title;
    @Schema(description = "문서 유형", example = "BUSINESS_PLAN")
    private final DocumentType docType;
    @Schema(description = "기준일자", example = "2026-01-31")
    private final LocalDate baseDate;
    @Schema(description = "분석 결과 ID", example = "10")
    private final Long analysisId;
    @Schema(description = "총 리스크 점수", example = "42")
    private final Integer totalRiskScore;
    @Schema(description = "파싱 JSON S3 키", example = "parsed/123/abc_1a2b3c4d.json.gz")
    private final String parsedJsonS3Key;
}