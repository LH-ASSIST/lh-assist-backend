package com.lh.assist.document.api.mapper;

import com.lh.assist.document.api.dto.response.DocumentResponse;
import com.lh.assist.document.api.dto.response.DocumentWithAnalysisResponse;
import com.lh.assist.document.api.dto.response.DocumentAccessType;
import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.document.domain.entity.Document;

public final class DocumentMapper {
	private DocumentMapper() {
	}

	public static DocumentResponse toResponse(Document document) {
		return toResponse(document, DocumentAccessType.OWNER);
	}

	public static DocumentResponse toResponse(
			Document document,
			DocumentAccessType accessType
	) {
		return DocumentResponse.builder()
				.docId(document.getDocId())
				.title(document.getTitle())
				.docType(document.getDocType())
				.baseDate(document.getBaseDate())
				.analysisStatus(document.getAnalysisStatus())
				.approvalStatus(document.getApprovalStatus())
				.metadataStatus(document.getMetadataStatus())
				.accessType(accessType)
				.createdAt(document.getCreatedAt())
				.build();
	}

	public static DocumentWithAnalysisResponse toWithAnalysisResponse(
			Document document,
			AnalysisResult latestResult,
			DocumentAccessType accessType
	) {
		return DocumentWithAnalysisResponse.builder()
				.docId(document.getDocId())
				.title(document.getTitle())
				.docType(document.getDocType())
				.baseDate(document.getBaseDate())
				.analysisStatus(document.getAnalysisStatus())
				.approvalStatus(document.getApprovalStatus())
				.metadataStatus(document.getMetadataStatus())
				.accessType(accessType)
				.analysisId(latestResult != null ? latestResult.getAnalysisId() : null)
				.analysisResultStatus(latestResult != null ? latestResult.getStatus() : null)
				.totalRiskScore(latestResult != null ? latestResult.getTotalRiskScore() : null)
				.createdAt(document.getCreatedAt())
				.build();
	}
}