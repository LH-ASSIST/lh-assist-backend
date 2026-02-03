package com.lh.assist.document.api.mapper;

import com.lh.assist.document.api.dto.response.DocumentResponse;
import com.lh.assist.document.api.dto.response.DocumentWithAnalysisResponse;
import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.document.domain.entity.Document;

public final class DocumentMapper {
	private DocumentMapper() {
	}

	public static DocumentResponse toResponse(Document document) {
		return DocumentResponse.builder()
				.docId(document.getDocId())
				.title(document.getTitle())
				.docType(document.getDocType())
				.baseDate(document.getBaseDate())
				.analysisStatus(document.getAnalysisStatus())
				.approvalStatus(document.getApprovalStatus())
				.metadataStatus(document.getMetadataStatus())
				.createdAt(document.getCreatedAt())
				.build();
	}

	public static DocumentWithAnalysisResponse toWithAnalysisResponse(
			Document document,
			AnalysisResult latestResult
	) {
		return DocumentWithAnalysisResponse.builder()
				.docId(document.getDocId())
				.title(document.getTitle())
				.docType(document.getDocType())
				.baseDate(document.getBaseDate())
				.analysisStatus(document.getAnalysisStatus())
				.approvalStatus(document.getApprovalStatus())
				.metadataStatus(document.getMetadataStatus())
				.analysisId(latestResult != null ? latestResult.getAnalysisId() : null)
				.analysisResultStatus(latestResult != null ? latestResult.getStatus() : null)
				.totalRiskScore(latestResult != null ? latestResult.getTotalRiskScore() : null)
				.createdAt(document.getCreatedAt())
				.build();
	}
}