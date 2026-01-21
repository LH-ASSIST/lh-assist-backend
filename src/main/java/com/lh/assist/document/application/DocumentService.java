package com.lh.assist.document.application;

import com.lh.assist.common.exception.DocumentException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.SystemException;
import com.lh.assist.audit.domain.AuditLog;
import com.lh.assist.audit.domain.AuditLogRepository;
import com.lh.assist.document.domain.Document;
import com.lh.assist.document.domain.DocumentRepository;
import com.lh.assist.document.domain.DocumentType;
import com.lh.assist.user.domain.User;
import com.lh.assist.user.domain.UserRepository;
import com.lh.assist.infrastructure.aws.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

	private final DocumentRepository documentRepository;
	private final AuditLogRepository auditLogRepository;
	private final UserRepository userRepository;
	private final S3Service s3Service;

	@Transactional
	public Document uploadDocument(
			Long userId,
			DocumentType documentType,
			LocalDate baseDate,
			MultipartFile file
	) {
		if (userId == null || file == null || file.isEmpty()) {
			throw new DocumentException(ErrorCode.INVALID_INPUT_VALUE);
		}

		validateFile(file);

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new DocumentException(ErrorCode.INVALID_INPUT_VALUE));

		String keyPrefix = "documents/" + userId;
		String s3Key = s3Service.uploadFile(file, keyPrefix);
		String resolvedTitle = resolveTitle(file);
		DocumentType resolvedType = resolveDocumentType(documentType);
		LocalDate resolvedBaseDate = resolveBaseDate(baseDate);

		try {
			Document document = Document.builder()
					.title(resolvedTitle)
					.docType(resolvedType)
					.s3Key(s3Key)
					.baseDate(resolvedBaseDate)
					.user(user)
					.build();

			Document saved = documentRepository.save(document);

			AuditLog auditLog = AuditLog.builder()
					.actionType("DOCUMENT_UPLOAD")
					.targetType("DOCUMENT")
					.targetId(saved.getDocId())
					.s3Key(s3Key)
					.actor(user)
					.build();
			auditLogRepository.save(auditLog);

			return saved;
		} catch (RuntimeException ex) {
			log.warn(
					"DB 저장 실패로 S3 보상 삭제를 시도합니다. s3Key={}, userId={}, reason={}",
					s3Key,
					userId,
					ex.getMessage(),
					ex
			);
			try {
				s3Service.deleteFile(s3Key);
			} catch (RuntimeException cleanupEx) {
				log.error(
						"DB 실패 이후 S3 보상 삭제에 실패했습니다. s3Key={}, userId={}",
						s3Key,
						userId,
						cleanupEx
				);
			}
			if (ex instanceof DocumentException) {
				throw ex;
			}
			if (ex instanceof SystemException) {
				throw ex;
			}
			throw new SystemException(ErrorCode.INTERNAL_SERVER_ERROR, ex);
		}
	}

	public Document uploadDocumentByEmail(
			String email,
			DocumentType documentType,
			LocalDate baseDate,
			MultipartFile file
	) {
		User user = getUserByEmail(email);

		return uploadDocument(user.getUserId(), documentType, baseDate, file);
	}

	@Transactional(readOnly = true)
	public Document getDocumentByEmail(String email, Long docId) {
		User user = getUserByEmail(email);
		Document document = getDocumentById(docId);
		if (!document.getUser().equals(user)) {
			throw new DocumentException(ErrorCode.ACCESS_DENIED);
		}
		return document;
	}

	@Transactional(readOnly = true)
	public List<Document> getDocumentsByEmail(String email) {
		User user = getUserByEmail(email);
		return documentRepository.findAllByUser_UserIdOrderByCreatedAtDesc(user.getUserId());
	}

	@Transactional
	public void deleteDocumentByEmail(String email, Long docId) {
		User user = getUserByEmail(email);
		Document document = getDocumentById(docId);
		if (!document.getUser().equals(user)) {
			throw new DocumentException(ErrorCode.ACCESS_DENIED);
		}

		String s3Key = document.getS3Key();
		documentRepository.delete(document);
		try {
			s3Service.deleteFile(s3Key);
		} catch (RuntimeException ex) {
			log.warn(
					"S3 삭제 실패로 문서 삭제를 롤백합니다. s3Key={}, docId={}, reason={}",
					s3Key,
					docId,
					ex.getMessage(),
					ex
			);
			throw ex;
		}
	}

	private void validateFile(MultipartFile file) {
		String originalFilename = file.getOriginalFilename();
		if (originalFilename == null || originalFilename.isBlank()) {
			throw new DocumentException(ErrorCode.DOCUMENT_NOT_SUPPORTED);
		}

		String filename = originalFilename.trim().toLowerCase();
		if (!(filename.endsWith(".pdf") || filename.endsWith(".hwp"))) {
			throw new DocumentException(ErrorCode.DOCUMENT_NOT_SUPPORTED);
		}
	}

	private String resolveTitle(MultipartFile file) {
		String originalFilename = file.getOriginalFilename();
		if (originalFilename == null || originalFilename.isBlank()) {
			return "업로드 문서";
		}
		return originalFilename.trim();
	}

	private DocumentType resolveDocumentType(DocumentType documentType) {
		return documentType != null ? documentType : DocumentType.ETC;
	}

	private LocalDate resolveBaseDate(LocalDate baseDate) {
		return baseDate != null ? baseDate : LocalDate.now();
	}

	private User getUserByEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new DocumentException(ErrorCode.UNAUTHORIZED);
		}

		return userRepository.findByEmail(email)
				.orElseThrow(() -> new DocumentException(ErrorCode.UNAUTHORIZED));
	}

	private Document getDocumentById(Long docId) {
		if (docId == null) {
			throw new DocumentException(ErrorCode.INVALID_INPUT_VALUE);
		}

		return documentRepository.findById(docId)
				.orElseThrow(() -> new DocumentException(ErrorCode.DOCUMENT_NOT_FOUND));
	}
}
