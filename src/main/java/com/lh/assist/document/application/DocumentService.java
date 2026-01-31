package com.lh.assist.document.application;

import com.lh.assist.common.exception.DocumentException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.SystemException;
import com.lh.assist.audit.domain.entity.AuditLog;
import com.lh.assist.audit.domain.repository.AuditLogRepository;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.document.domain.enums.DocumentType;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
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

	/**
	 * 문서를 업로드하고 저장된 문서 엔티티를 반환한다
	 *
	 * @param userId 업로드 요청 사용자 ID
	 * @param documentType 문서 유형
	 * @param baseDate 기준 일자
	 * @param file 업로드 파일
	 * @return 저장된 문서 엔티티
	 */
	@Transactional
	public Document uploadDocument(
			Long userId,
			DocumentType documentType,
			LocalDate baseDate,
			MultipartFile file
	) {
		return doUploadDocument(userId, documentType, baseDate, file);
	}

	/**
	 * 이메일로 사용자를 조회한 뒤 문서를 업로드한다
	 *
	 * @param email 사용자 이메일
	 * @param documentType 문서 유형
	 * @param baseDate 기준 일자
	 * @param file 업로드 파일
	 * @return 저장된 문서 엔티티
	 */
	@Transactional
	public Document uploadDocumentByEmail(
			String email,
			DocumentType documentType,
			LocalDate baseDate,
			MultipartFile file
	) {
		User user = getUserByEmail(email);

		return doUploadDocument(user.getUserId(), documentType, baseDate, file);
	}

	/**
	 * 문서를 검증하고 S3 업로드 및 감사 로그 저장을 수행한다
	 *
	 * DB 저장 실패 시 업로드된 S3 파일을 보상 삭제한다
	 *
	 * @param userId 업로드 요청 사용자 ID
	 * @param documentType 문서 유형
	 * @param baseDate 기준 일자
	 * @param file 업로드 파일
	 * @return 저장된 문서 엔티티
	 */
	private Document doUploadDocument(
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

	/**
	 * 사용자 이메일로 문서 단건을 조회한다
	 *
	 * 문서 소유자가 아니면 접근을 차단한다
	 *
	 * @param email 사용자 이메일
	 * @param docId 문서 ID
	 * @return 조회된 문서 엔티티
	 */
	@Transactional(readOnly = true)
	public Document getDocumentByEmail(
			String email,
			Long docId
	) {
		User user = getUserByEmail(email);
		Document document = getDocumentById(docId);
		if (!document.getUser().equals(user)) {
			throw new DocumentException(ErrorCode.ACCESS_DENIED);
		}
		return document;
	}

	/**
	 * 사용자 이메일 기준으로 문서 목록을 조회한다
	 *
	 * @param email 사용자 이메일
	 * @return 사용자 문서 목록
	 */
	@Transactional(readOnly = true)
	public List<Document> getDocumentsByEmail(String email) {
		User user = getUserByEmail(email);
		return documentRepository.findAllByUser_UserIdOrderByCreatedAtDesc(user.getUserId());
	}

	/**
	 * 사용자 이메일 기준으로 문서를 삭제한다
	 *
	 * 문서 소유자가 아니면 접근을 차단한다
	 *
	 * @param email 사용자 이메일
	 * @param docId 문서 ID
	 */
	@Transactional
	public void deleteDocumentByEmail(
			String email,
			Long docId
	) {
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

	/**
	 * 업로드 파일의 이름과 확장자를 검증한다
	 *
	 * @param file 업로드 파일
	 */
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

	/**
	 * 업로드 파일 이름으로 문서 제목을 생성한다
	 *
	 * @param file 업로드 파일
	 * @return 문서 제목
	 */
	private String resolveTitle(MultipartFile file) {
		String originalFilename = file.getOriginalFilename();
		if (originalFilename == null || originalFilename.isBlank()) {
			return "업로드 문서";
		}
		return originalFilename.trim();
	}

	/**
	 * 문서 유형이 비어있으면 기본 유형을 설정한다
	 *
	 * @param documentType 요청 문서 유형
	 * @return 확정된 문서 유형
	 */
	private DocumentType resolveDocumentType(DocumentType documentType) {
		return documentType != null ? documentType : DocumentType.ETC;
	}

	/**
	 * 기준일이 비어있으면 현재 날짜를 반환한다
	 *
	 * @param baseDate 요청 기준 일자
	 * @return 확정된 기준 일자
	 */
	private LocalDate resolveBaseDate(LocalDate baseDate) {
		return baseDate != null ? baseDate : LocalDate.now();
	}

	/**
	 * 이메일로 사용자를 조회하고 존재하지 않으면 예외를 발생시킨다
	 *
	 * @param email 사용자 이메일
	 * @return 조회된 사용자 엔티티
	 */
	private User getUserByEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new DocumentException(ErrorCode.UNAUTHORIZED);
		}

		return userRepository.findByEmail(email)
				.orElseThrow(() -> new DocumentException(ErrorCode.UNAUTHORIZED));
	}

	/**
	 * 문서 ID로 문서를 조회하고 존재하지 않으면 예외를 발생시킨다
	 *
	 * @param docId 문서 ID
	 * @return 조회된 문서 엔티티
	 */
	private Document getDocumentById(Long docId) {
		if (docId == null) {
			throw new DocumentException(ErrorCode.INVALID_INPUT_VALUE);
		}

		return documentRepository.findById(docId)
				.orElseThrow(() -> new DocumentException(ErrorCode.DOCUMENT_NOT_FOUND));
	}
}
