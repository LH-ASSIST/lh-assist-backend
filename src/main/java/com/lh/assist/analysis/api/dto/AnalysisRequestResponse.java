package com.lh.assist.analysis.api.dto;

import com.lh.assist.analysis.domain.AnalysisJobStatus;
import com.lh.assist.analysis.domain.AnalysisResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "분석 요청 응답")
public class AnalysisRequestResponse {
	@Schema(description = "분석 ID", example = "1001")
	private final Long analysisId;
	@Schema(description = "작업 ID", example = "2001")
	private final Long jobId;
	@Schema(description = "분석 상태", example = "REQUESTED")
	private final AnalysisResultStatus analysisStatus;
	@Schema(description = "작업 상태", example = "REQUESTED")
	private final AnalysisJobStatus jobStatus;
	@Schema(description = "기준일", example = "2026-01-19")
	private final LocalDate baseDate;
	@Schema(description = "요청 시각", example = "2026-01-19T10:22:11")
	private final LocalDateTime createdAt;
}