package com.lh.assist.approval.application;

import com.lh.assist.approval.api.dto.response.DocumentApprovalResponse;
import com.lh.assist.approval.api.mapper.DocumentApprovalMapper;
import com.lh.assist.approval.domain.entity.DocumentApproval;
import com.lh.assist.approval.domain.repository.DocumentApprovalRepository;
import com.lh.assist.audit.application.AuditLogService;
import com.lh.assist.audit.domain.enums.AuditActionType;
import com.lh.assist.audit.domain.enums.AuditTargetType;
import com.lh.assist.common.exception.BusinessException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.security.UserPrincipal;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.ApprovalStatus;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.user.api.dto.response.UserListResponse;
import com.lh.assist.user.api.mapper.UserMapper;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserStatus;
import com.lh.assist.user.domain.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentApprovalService {

    private final DocumentRepository documentRepository;
    private final DocumentApprovalRepository approvalRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /**
     * 문서 승인 상세 정보를 조회한다
     *
     * 문서 소유자/상위권자/관리자만 조회 가능하다
     *
     * @param docId 문서 ID
     * @param principal 요청 사용자
     * @return 승인 상세 응답
     */
    @Transactional(readOnly = true)
    public DocumentApprovalResponse getApproval(
            Long docId,
            UserPrincipal principal
    ) {
        Document document = getDocumentById(docId);
        DocumentApproval approval = approvalRepository.findByDocument_DocId(docId).orElse(null);
        validateViewer(document, approval, principal);
        User requester = getUserById(principal.userId());
        List<UserListResponse> candidates = getApproverCandidates(requester);
        return DocumentApprovalMapper.toResponse(document, approval, candidates);
    }

    /**
     * 문서에 상위권자를 지정한다
     *
     * 관리자만 실행 가능하며 지정 시 승인 상태는 WAITING으로 초기화된다
     *
     * @param docId 문서 ID
     * @param approverId 상위권자 ID
     * @param principal 요청 사용자
     * @return 승인 상세 응답
     */
    @Transactional
    public DocumentApprovalResponse assignApprover(
            Long docId,
            Long approverId,
            UserPrincipal principal
    ) {
        if (principal == null || principal.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Document document = getDocumentById(docId);
        if (!principal.isAdmin() && !document.getUser().getUserId().equals(principal.userId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        User approver = getUserById(approverId);

        DocumentApproval approval = approvalRepository.findByDocument_DocId(docId)
                .orElseGet(() -> DocumentApproval.builder()
                        .document(document)
                        .status(ApprovalStatus.WAITING)
                        .build());

        approval.assignReviewer(
                approver.getUserId(),
                approver.getName(),
                approver.getPosition().getDescription(),
                approver.getDepartment().getDescription()
        );
        document.updateApprovalStatus(ApprovalStatus.WAITING);
        approvalRepository.save(approval);
        User admin = getUserById(principal.userId());
        auditLogService.log(
                AuditActionType.DOCUMENT_APPROVAL_ASSIGNED,
                AuditTargetType.DOCUMENT_APPROVAL,
                approval.getApprovalId(),
                document.getS3Key(),
                admin
        );

        return DocumentApprovalMapper.toResponse(document, approval);
    }

    /**
     * 상위권자가 문서를 승인 또는 반려한다
     *
     * @param docId 문서 ID
     * @param status 승인 상태 (APPROVED/REJECTED)
     * @param reviewComment 검토 의견
     * @param principal 요청 사용자
     * @return 승인 상세 응답
     */
    @Transactional
    public DocumentApprovalResponse review(
            Long docId,
            ApprovalStatus status,
            String reviewComment,
            UserPrincipal principal
    ) {
        if (principal == null || principal.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (status == null || status == ApprovalStatus.WAITING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Document document = getDocumentById(docId);
        DocumentApproval approval = approvalRepository.findByDocument_DocId(docId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        if (approval.getStatus() != ApprovalStatus.WAITING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (approval.getApproverId() == null || !approval.getApproverId().equals(principal.userId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        User reviewer = getUserById(principal.userId());
        approval.assignReviewer(
                reviewer.getUserId(),
                reviewer.getName(),
                reviewer.getPosition().getDescription(),
                reviewer.getDepartment().getDescription()
        );
        approval.markReviewed(status, LocalDateTime.now(), reviewComment);
        document.updateApprovalStatus(status);
        auditLogService.log(
                AuditActionType.DOCUMENT_APPROVAL_REVIEWED,
                AuditTargetType.DOCUMENT_APPROVAL,
                approval.getApprovalId(),
                document.getS3Key(),
                reviewer
        );

        return DocumentApprovalMapper.toResponse(document, approval);
    }

    private Document getDocumentById(Long docId) {
        if (docId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return documentRepository.findById(docId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    /**
     * 사용자 ID로 사용자 엔티티를 조회한다.
     *
     * @param userId 사용자 ID
     * @return 사용자 엔티티
     */
    private User getUserById(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 문서 승인 상세 조회 권한을 검증한다.
     *
     * 문서 소유자, 상위권자(승인자), 관리자만 조회 가능하다.
     *
     * @param document 문서
     * @param approval 승인 정보 (없을 수 있음)
     * @param principal 요청 사용자
     */
    private void validateViewer(
            Document document,
            DocumentApproval approval,
            UserPrincipal principal
    ) {
        if (principal == null || principal.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (principal.isAdmin()) {
            return;
        }
        Long userId = principal.userId();
        if (document.getUser().getUserId().equals(userId)) {
            return;
        }
        if (approval != null && userId.equals(approval.getApproverId())) {
            return;
        }
        throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    /**
     * 상위권자 후보 목록을 조회한다.
     *
     * 요청 사용자와 같은 부서의 ACTIVE 사용자 중 본인을 제외한 목록을 반환한다.
     *
     * @param requester 요청 사용자
     * @return 상위권자 후보 목록
     */
    private List<UserListResponse> getApproverCandidates(User requester) {
        UserDepartment department = requester.getDepartment();
        if (department == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return userRepository.findAllByDepartmentAndStatus(department, UserStatus.ACTIVE).stream()
                .filter(user -> user.getUserId() != null && !user.getUserId().equals(requester.getUserId()))
                .map(UserMapper::toListResponse)
                .toList();
    }
}
