package com.lh.assist.document.application;

import com.lh.assist.common.exception.DocumentException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.exception.SystemException;
import com.lh.assist.audit.application.AuditLogService;
import com.lh.assist.audit.domain.enums.AuditActionType;
import com.lh.assist.audit.domain.enums.AuditTargetType;
import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.repository.AnalysisResultRepository;
import com.lh.assist.approval.domain.repository.DocumentApprovalRepository;
import com.lh.assist.document.api.dto.response.DocumentAccessType;
import com.lh.assist.document.api.dto.response.DocumentResponse;
import com.lh.assist.document.api.dto.response.DocumentWithAnalysisResponse;
import com.lh.assist.document.api.mapper.DocumentMapper;
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

import java.net.URL;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

	private final DocumentRepository documentRepository;
	private final AuditLogService auditLogService;
	private final UserRepository userRepository;
	private final S3Service s3Service;
	private final AnalysisResultRepository analysisResultRepository;
	private final DocumentApprovalRepository approvalRepository;

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

			auditLogService.log(
					AuditActionType.DOCUMENT_UPLOAD,
					AuditTargetType.DOCUMENT,
					saved.getDocId(),
					s3Key,
					user
			);

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
	 * 문서 소유자 또는 승인 담당자가 아니면 접근을 차단한다
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
		if (!document.getUser().equals(user) && !isApprover(user.getUserId(), docId)) {
			throw new DocumentException(ErrorCode.ACCESS_DENIED);
		}
		return document;
	}

	/**
	 * 사용자 이메일로 문서 단건 응답을 조회한다
	 *
	 * 문서 소유자 또는 승인 담당자가 아니면 접근을 차단한다
	 *
	 * @param email 사용자 이메일
	 * @param docId 문서 ID
	 * @return 조회된 문서 응답
	 */
	@Transactional(readOnly = true)
	public DocumentResponse getDocumentResponseByEmail(
			String email,
			Long docId
	) {
		User user = getUserByEmail(email);
		Document document = getDocumentById(docId);
		DocumentAccessType accessType = resolveAccessType(user.getUserId(), document);
		if (accessType == null) {
			throw new DocumentException(ErrorCode.ACCESS_DENIED);
		}
		return DocumentMapper.toResponse(document, accessType);
	}

	/**
	 * 사용자 이메일 기준으로 문서 목록을 조회한다
	 *
	 * 문서 소유자 또는 승인 담당자로 지정된 문서를 포함한다
	 *
	 * @param email 사용자 이메일
	 * @return 사용자 문서 목록
	 */
	@Transactional(readOnly = true)
	public List<Document> getDocumentsByEmail(String email) {
		User user = getUserByEmail(email);
		return documentRepository.findAllAccessibleByUserIdOrderByCreatedAtDesc(user.getUserId());
	}

	/**
	 * 사용자 이메일 기준으로 문서 목록 응답을 조회한다
	 *
	 * 문서 소유자 또는 승인 담당자로 지정된 문서를 포함한다
	 *
	 * @param email 사용자 이메일
	 * @return 사용자 문서 목록 응답
	 */
	@Transactional(readOnly = true)
	public List<DocumentResponse> getDocumentResponsesByEmail(String email) {
		User user = getUserByEmail(email);
		List<Document> documents = documentRepository
				.findAllAccessibleByUserIdOrderByCreatedAtDesc(user.getUserId());
		Set<Long> approverDocIds = getApproverDocIds(user.getUserId());
		return documents.stream()
				.map(document -> DocumentMapper.toResponse(
						document,
						resolveAccessType(user.getUserId(), document, approverDocIds)
				))
				.toList();
	}

	/**
	 * 문서 목록과 최신 분석 요약 정보를 함께 조회한다
	 *
	 * 분석 결과가 없으면 요약 필드는 null로 반환한다
	 * 문서 소유자 또는 승인 담당자로 지정된 문서를 포함한다
	 *
	 * @param email 사용자 이메일
	 * @return 문서 + 분석 요약 목록
	 */
	@Transactional(readOnly = true)
	public List<DocumentWithAnalysisResponse> getDocumentsWithAnalysisByEmail(
			String email
	) {
		User user = getUserByEmail(email);
		List<Document> documents = documentRepository
				.findAllAccessibleByUserIdOrderByCreatedAtDesc(user.getUserId());
		Set<Long> approverDocIds = getApproverDocIds(user.getUserId());
		return documents.stream()
				.map(document -> {
					AnalysisResult latestResult = analysisResultRepository
							.findTopByDocument_DocIdOrderByCreatedAtDesc(document.getDocId())
							.orElse(null);
					DocumentAccessType accessType = resolveAccessType(
							user.getUserId(),
							document,
							approverDocIds
					);
					return DocumentMapper.toWithAnalysisResponse(document, latestResult, accessType);
				})
				.toList();
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
			auditLogService.log(
					AuditActionType.DOCUMENT_DELETE,
					AuditTargetType.DOCUMENT,
					document.getDocId(),
					s3Key,
					user
			);
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
	 * S3 원본문서 미리보기용 presigned URL을 발급한다
	 *
	 * @param document 대상 문서
	 * @param expiresIn URL 만료 시간
	 * @return presigned URL
	 */
	@Transactional(readOnly = true)
	public URL generatePreviewUrl(
			Document document,
			Duration expiresIn
	) {
		if (document == null) {
			throw new DocumentException(ErrorCode.INVALID_INPUT_VALUE);
		}
		return s3Service.generatePresignedUrl(document.getS3Key(), expiresIn);
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

	private Set<Long> getApproverDocIds(Long userId) {
		if (userId == null) {
			return Set.of();
		}
		return new HashSet<>(approvalRepository.findDocumentIdsByApproverId(userId));
	}

	private DocumentAccessType resolveAccessType(
			Long userId,
			Document document
	) {
		if (document == null || userId == null) {
			return null;
		}
		if (document.getUser().getUserId().equals(userId)) {
			return DocumentAccessType.OWNER;
		}
		return isApprover(userId, document.getDocId()) ? DocumentAccessType.APPROVER : null;
	}

	private DocumentAccessType resolveAccessType(
			Long userId,
			Document document,
			Set<Long> approverDocIds
	) {
		if (document == null || userId == null) {
			return null;
		}
		if (document.getUser().getUserId().equals(userId)) {
			return DocumentAccessType.OWNER;
		}
		if (approverDocIds != null && approverDocIds.contains(document.getDocId())) {
			return DocumentAccessType.APPROVER;
		}
		return null;
	}

	private boolean isApprover(
			Long userId,
			Long docId
	) {
		if (userId == null || docId == null) {
			return false;
		}
		return approvalRepository.existsByDocument_DocIdAndApproverId(docId, userId);
	}
}
