package com.lh.assist.document.api.mapper;

import com.lh.assist.document.api.dto.response.DocumentResponse;
import com.lh.assist.regulation.domain.enums.domain.entity.Document;

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
}