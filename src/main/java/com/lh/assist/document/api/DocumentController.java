package com.lh.assist.document.api;

import com.lh.assist.document.api.docs.*;
import com.lh.assist.document.api.dto.response.DocumentResponse;
import com.lh.assist.document.api.dto.response.DocumentPreviewUrlResponse;
import com.lh.assist.document.api.dto.response.DocumentWithAnalysisResponse;
import com.lh.assist.document.api.mapper.DocumentMapper;
import com.lh.assist.analysis.api.dto.response.AnalysisRequestResponse;
import com.lh.assist.analysis.application.AnalysisService;
import com.lh.assist.document.application.DocumentService;
import com.lh.assist.common.model.ApiResponse;
import com.lh.assist.common.exception.DocumentException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.security.UserPrincipal;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.DocumentType;
import java.net.URL;
import java.util.List;
import java.time.LocalDate;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/documents")
@DocumentApiDocs
public class DocumentController {

	private final DocumentService documentService;
	private final AnalysisService analysisService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("isAuthenticated()")
	@DocumentUploadDocs
	public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
			@AuthenticationPrincipal UserPrincipal principal,
			@RequestParam("file") MultipartFile file,
			@RequestParam("docType") DocumentType docType,
			@HiddenParamDocs
			@RequestParam(value = "baseDate", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate
	) {
		String email = principal.email();
		Document document = documentService.uploadDocumentByEmail(email, docType, baseDate, file);
		DocumentResponse response = DocumentMapper.toResponse(document);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
	}

	@GetMapping("/{docId}")
	@PreAuthorize("isAuthenticated()")
	@DocumentGetDocs
	public ResponseEntity<ApiResponse<DocumentResponse>> getDocument(
			@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long docId
	) {
		DocumentResponse response = documentService.getDocumentResponseByEmail(principal.email(), docId);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	@DocumentListDocs
	public ResponseEntity<ApiResponse<List<DocumentResponse>>> getMyDocuments(
			@AuthenticationPrincipal UserPrincipal principal
	) {
		List<DocumentResponse> responses = documentService.getDocumentResponsesByEmail(principal.email());
		return ResponseEntity.ok(ApiResponse.success(responses));
	}

	@GetMapping("/with-analysis")
	@PreAuthorize("isAuthenticated()")
	@DocumentListWithAnalysisDocs
	public ResponseEntity<ApiResponse<List<DocumentWithAnalysisResponse>>> getMyDocumentsWithAnalysis(
			@AuthenticationPrincipal UserPrincipal principal
	) {
		List<DocumentWithAnalysisResponse> responses =
				documentService.getDocumentsWithAnalysisByEmail(principal.email());
		return ResponseEntity.ok(ApiResponse.success(responses));
	}

	@GetMapping("/{docId}/preview-url")
	@PreAuthorize("isAuthenticated()")
	@DocumentPreviewUrlDocs
	public ResponseEntity<ApiResponse<DocumentPreviewUrlResponse>> getPreviewUrl(
			@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long docId,
			@RequestParam(value = "expiresMinutes", required = false) Integer expiresMinutes
	) {
		int resolvedMinutes = expiresMinutes == null ? 60 : expiresMinutes;
		if (resolvedMinutes < 1 || resolvedMinutes > 1440) {
			throw new DocumentException(ErrorCode.INVALID_INPUT_VALUE);
		}

		Document document = documentService.getDocumentByEmail(principal.email(), docId);
		Duration expiresIn = Duration.ofMinutes(resolvedMinutes);
		Instant issuedAt = Instant.now();
		URL presigned = documentService.generatePreviewUrl(document, expiresIn);
		DocumentPreviewUrlResponse response = DocumentPreviewUrlResponse.builder()
				.url(presigned.toString())
				.expiresAt(issuedAt.plus(expiresIn))
				.build();
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@DeleteMapping("/{docId}")
	@PreAuthorize("isAuthenticated()")
	@DocumentDeleteDocs
	public ResponseEntity<ApiResponse<Void>> deleteDocument(
			@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long docId
	) {
		String email = principal.email();
		documentService.deleteDocumentByEmail(email, docId);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.noContent());
	}

	@PostMapping("/{docId}/analyses")
	@PreAuthorize("isAuthenticated()")
	@DocumentAnalysisRequestDocs
	public ResponseEntity<ApiResponse<AnalysisRequestResponse>> requestAnalysis(
			@AuthenticationPrincipal UserPrincipal principal,
			@PathVariable Long docId,
			@RequestParam(value = "baseDate", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate
	) {
		String email = principal.email();
		AnalysisRequestResponse response = analysisService.requestAnalysisByEmail(docId, email, baseDate);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
	}
}
