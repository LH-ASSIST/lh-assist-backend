package com.lh.assist.document.api;

import com.lh.assist.document.api.dto.DocumentResponse;
import com.lh.assist.document.api.mapper.DocumentMapper;
import com.lh.assist.document.api.docs.DocumentApiDocs;
import com.lh.assist.document.api.docs.DocumentUploadDocs;
import com.lh.assist.document.api.docs.HiddenParamDocs;
import com.lh.assist.document.application.DocumentService;
import com.lh.assist.common.model.ApiResponse;
import com.lh.assist.document.domain.Document;
import com.lh.assist.document.domain.DocumentType;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
}
