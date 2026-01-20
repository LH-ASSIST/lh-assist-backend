package com.lh.assist.document.application;

import com.lh.assist.common.exception.BusinessException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.SystemException;
import com.lh.assist.audit.domain.AuditLog;
import com.lh.assist.audit.domain.AuditLogRepository;
import com.lh.assist.document.domain.Document;
import com.lh.assist.document.domain.DocumentRepository;
import com.lh.assist.document.domain.DocumentType;
import com.lh.assist.user.domain.User;
import com.lh.assist.user.domain.UserRepository;
import com.lh.assist.document.infrastructure.aws.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

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
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		validateFile(file);

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

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
			if (ex instanceof BusinessException) {
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
		if (email == null || email.isBlank()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}

		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

		return uploadDocument(user.getUserId(), documentType, baseDate, file);
	}

	private void validateFile(MultipartFile file) {
		String originalFilename = file.getOriginalFilename();
		if (originalFilename == null || originalFilename.isBlank()) {
			throw new BusinessException(ErrorCode.DOCUMENT_NOT_SUPPORTED);
		}

		String filename = originalFilename.trim().toLowerCase();
		if (!(filename.endsWith(".pdf") || filename.endsWith(".hwp"))) {
			throw new BusinessException(ErrorCode.DOCUMENT_NOT_SUPPORTED);
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

}
