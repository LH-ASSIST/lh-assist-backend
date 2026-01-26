package com.lh.assist.document.api.dto.response;

import com.lh.assist.regulation.domain.enums.domain.enums.AnalysisStatus;
import com.lh.assist.regulation.domain.enums.domain.enums.ApprovalStatus;
import com.lh.assist.regulation.domain.enums.domain.enums.DocumentType;
import com.lh.assist.regulation.domain.enums.domain.enums.MetadataStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Builder
@Schema(description = "문서 업로드 응답")
public class DocumentResponse {
	@Schema(description = "문서 ID", example = "123")
	private final Long docId;
	@Schema(description = "문서 제목", example = "2026년 1차 입주자 모집 공고")
	private final String title;
	@Schema(description = "문서 유형", example = "NOTICE")
	private final DocumentType docType;
	@Schema(description = "기준일", example = "2026-01-19")
	private final LocalDate baseDate;
	@Schema(description = "분석 상태", example = "PENDING")
	private final AnalysisStatus analysisStatus;
	@Schema(description = "승인 상태", example = "WAITING")
	private final ApprovalStatus approvalStatus;
	@Schema(description = "메타데이터 처리 상태", example = "PENDING")
	private final MetadataStatus metadataStatus;
	@Schema(description = "생성일시", example = "2026-01-19T10:22:11")
	private final LocalDateTime createdAt;
}
