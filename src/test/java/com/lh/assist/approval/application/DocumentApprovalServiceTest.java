package com.lh.assist.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lh.assist.approval.domain.entity.DocumentApproval;
import com.lh.assist.approval.domain.repository.DocumentApprovalRepository;
import com.lh.assist.common.exception.BusinessException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.security.UserPrincipal;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.ApprovalStatus;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.support.ReflectionTestUtils;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentApprovalServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentApprovalRepository approvalRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DocumentApprovalService approvalService;

    @Test
    @DisplayName("관리자가 상위권자를 지정하면 승인 상태가 WAITING이어야 한다")
    void 상위권자_지정_성공() {
        User admin = TestDataFactory.admin("admin@lh.com");
        ReflectionTestUtils.setField(admin, "userId", 1L);
        User approver = TestDataFactory.user("approver@lh.com");
        ReflectionTestUtils.setField(approver, "userId", 2L);

        Document document = Document.builder()
                .title("문서")
                .s3Key("documents/1/key.pdf")
                .baseDate(java.time.LocalDate.now())
                .user(approver)
                .build();
        ReflectionTestUtils.setField(document, "docId", 10L);

        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(userRepository.findById(2L)).thenReturn(Optional.of(approver));
        when(approvalRepository.findByDocument_DocId(10L)).thenReturn(Optional.empty());

        UserPrincipal principal = new UserPrincipal(admin.getUserId(), admin.getEmail(), admin.getRole().name());
        var response = approvalService.assignApprover(10L, 2L, principal);

        assertThat(response.getApprovalStatus()).isEqualTo(ApprovalStatus.WAITING);
        assertThat(response.getReviewerName()).isEqualTo(approver.getName());
        assertThat(response.getReviewerTitle()).isEqualTo(approver.getPosition().getDescription());
        assertThat(response.getReviewerDept()).isEqualTo(approver.getDepartment().getDescription());
        verify(approvalRepository).save(org.mockito.ArgumentMatchers.any(DocumentApproval.class));
    }

    @Test
    @DisplayName("관리자가 아니면 상위권자 지정이 거부되어야 한다")
    void 상위권자_지정_권한_없음() {
        User user = TestDataFactory.user("user@lh.com");
        ReflectionTestUtils.setField(user, "userId", 1L);

        UserPrincipal principal = new UserPrincipal(user.getUserId(), user.getEmail(), user.getRole().name());

        assertThatThrownBy(() -> approvalService.assignApprover(10L, 2L, principal))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("지정된 상위권자가 아니면 승인/반려가 거부되어야 한다")
    void 승인_반려_권한_없음() {
        User owner = TestDataFactory.user("owner@lh.com");
        ReflectionTestUtils.setField(owner, "userId", 1L);
        User approver = TestDataFactory.user("approver@lh.com");
        ReflectionTestUtils.setField(approver, "userId", 2L);
        User other = TestDataFactory.user("other@lh.com");
        ReflectionTestUtils.setField(other, "userId", 3L);

        Document document = Document.builder()
                .title("문서")
                .s3Key("documents/1/key.pdf")
                .baseDate(java.time.LocalDate.now())
                .user(owner)
                .build();
        ReflectionTestUtils.setField(document, "docId", 20L);

        DocumentApproval approval = DocumentApproval.builder()
                .document(document)
                .approverId(approver.getUserId())
                .status(ApprovalStatus.WAITING)
                .build();

        when(documentRepository.findById(20L)).thenReturn(Optional.of(document));
        when(approvalRepository.findByDocument_DocId(20L)).thenReturn(Optional.of(approval));

        UserPrincipal principal = new UserPrincipal(other.getUserId(), other.getEmail(), other.getRole().name());

        assertThatThrownBy(() -> approvalService.review(20L, ApprovalStatus.APPROVED, "ok", principal))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("승인 상태가 WAITING이면 입력값 오류가 발생해야 한다")
    void 승인_상태_대기_불가() {
        User approver = TestDataFactory.user("approver@lh.com");
        ReflectionTestUtils.setField(approver, "userId", 2L);

        UserPrincipal principal = new UserPrincipal(approver.getUserId(), approver.getEmail(), approver.getRole().name());

        assertThatThrownBy(() -> approvalService.review(30L, ApprovalStatus.WAITING, null, principal))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}