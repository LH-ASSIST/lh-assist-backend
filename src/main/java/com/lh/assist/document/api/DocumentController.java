package com.lh.assist.document.api;

import com.lh.assist.document.api.dto.DocumentResponse;
import com.lh.assist.document.api.mapper.DocumentMapper;
import com.lh.assist.document.api.docs.DocumentApiDocs;
import com.lh.assist.document.api.docs.DocumentAnalysisRequestDocs;
import com.lh.assist.document.api.docs.DocumentDeleteDocs;
import com.lh.assist.document.api.docs.DocumentGetDocs;
import com.lh.assist.document.api.docs.DocumentListDocs;
import com.lh.assist.document.api.docs.DocumentUploadDocs;
import com.lh.assist.document.api.docs.HiddenParamDocs;
import com.lh.assist.analysis.api.dto.AnalysisRequestResponse;
import com.lh.assist.analysis.application.AnalysisService;
import com.lh.assist.document.application.DocumentService;
import com.lh.assist.common.model.ApiResponse;
import com.lh.assist.document.domain.Document;
import com.lh.assist.document.domain.DocumentType;
import java.util.List;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/documents")
@DocumentApiDocs
public class DocumentController {

	private final DocumentService documentService;
	private final AnalysisService analysisService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@DocumentUploadDocs
	public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
			Authentication authentication,
			@RequestParam("file") MultipartFile file,
			@RequestParam("docType") DocumentType docType,
			@HiddenParamDocs
			@RequestParam(value = "baseDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate
	) {
		String email = authentication != null ? authentication.getName() : null;
		Document document = documentService.uploadDocumentByEmail(email, docType, baseDate, file);
		DocumentResponse response = DocumentMapper.toResponse(document);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
	}

	@GetMapping("/{docId}")
	@DocumentGetDocs
	public ResponseEntity<ApiResponse<DocumentResponse>> getDocument(
			Authentication authentication,
            @PathVariable Long docId
	) {
		String email = authentication != null ? authentication.getName() : null;
		Document document = documentService.getDocumentByEmail(email, docId);
		DocumentResponse response = DocumentMapper.toResponse(document);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@GetMapping
	@DocumentListDocs
	public ResponseEntity<ApiResponse<List<DocumentResponse>>> getMyDocuments(
			Authentication authentication
	) {
		String email = authentication != null ? authentication.getName() : null;
		List<DocumentResponse> responses = documentService.getDocumentsByEmail(email)
				.stream()
				.map(DocumentMapper::toResponse)
				.toList();
		return ResponseEntity.ok(ApiResponse.success(responses));
	}

	@DeleteMapping("/{docId}")
	@DocumentDeleteDocs
	public ResponseEntity<ApiResponse<Void>> deleteDocument(
			Authentication authentication,
            @PathVariable Long docId
	) {
		String email = authentication != null ? authentication.getName() : null;
		documentService.deleteDocumentByEmail(email, docId);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.noContent());
	}

	@PostMapping("/{docId}/analyses")
	@DocumentAnalysisRequestDocs
	public ResponseEntity<ApiResponse<AnalysisRequestResponse>> requestAnalysis(
			Authentication authentication,
			@PathVariable Long docId,
			@RequestParam(value = "baseDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate
	) {
		String email = authentication != null ? authentication.getName() : null;
		AnalysisRequestResponse response = analysisService.requestAnalysisByEmail(docId, email, baseDate);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
	}
}
