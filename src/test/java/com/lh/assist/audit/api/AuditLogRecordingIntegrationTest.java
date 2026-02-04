package com.lh.assist.audit.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.admin.api.dto.request.SuggestionAnswerRequest;
import com.lh.assist.approval.api.dto.request.ApprovalAssignRequest;
import com.lh.assist.approval.api.dto.request.ApprovalReviewRequest;
import com.lh.assist.audit.domain.entity.AuditLog;
import com.lh.assist.audit.domain.enums.AuditActionType;
import com.lh.assist.audit.domain.repository.AuditLogRepository;
import com.lh.assist.common.security.jwt.JwtTokenProvider;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.DocumentType;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.notice.api.dto.request.NoticeCreateRequest;
import com.lh.assist.suggestion.domain.entity.Suggestion;
import com.lh.assist.suggestion.domain.repository.SuggestionRepository;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.document.domain.enums.ApprovalStatus;
import com.lh.assist.user.domain.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditLogRecordingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SuggestionRepository suggestionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    @DisplayName("공지 등록 시 감사 로그가 저장되어야 한다")
    void 공지_등록_로그_저장() throws Exception {
        User admin = userRepository.save(TestDataFactory.admin("admin-notice@lh.com"));

        NoticeCreateRequest request = new NoticeCreateRequest("공지 제목", "공지 내용");

        mockMvc.perform(post("/api/v1/notice")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<AuditActionType> actions = auditLogRepository.findAll().stream()
                .map(AuditLog::getActionType)
                .toList();
        org.assertj.core.api.Assertions.assertThat(actions)
                .contains(AuditActionType.NOTICE_CREATED);
    }

    @Test
    @DisplayName("건의 답변 등록 시 감사 로그가 저장되어야 한다")
    void 건의_답변_로그_저장() throws Exception {
        User admin = userRepository.save(TestDataFactory.admin("admin-suggestion@lh.com"));
        User user = userRepository.save(TestDataFactory.user("user-suggestion@lh.com"));
        Suggestion suggestion = suggestionRepository.save(TestDataFactory.suggestion(user, false));

        SuggestionAnswerRequest request = new SuggestionAnswerRequest("답변 내용");

        mockMvc.perform(patch("/api/v1/admin/suggestions/{id}/answer", suggestion.getSuggestionId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        List<AuditActionType> actions = auditLogRepository.findAll().stream()
                .map(AuditLog::getActionType)
                .toList();
        org.assertj.core.api.Assertions.assertThat(actions)
                .contains(AuditActionType.SUGGESTION_ANSWERED);
    }

    @Test
    @DisplayName("승인자 지정 및 승인 처리 시 감사 로그가 저장되어야 한다")
    void 승인_로그_저장() throws Exception {
        User admin = userRepository.save(TestDataFactory.admin("audit-admin@lh.com"));
        User approver = userRepository.save(TestDataFactory.user("audit-approver@lh.com"));
        User owner = userRepository.save(TestDataFactory.user("audit-owner@lh.com"));

        Document document = documentRepository.save(Document.builder()
                .title("문서")
                .docType(DocumentType.PLAN)
                .s3Key("documents/test")
                .baseDate(LocalDate.now())
                .user(owner)
                .build());

        ApprovalAssignRequest assignRequest = new ApprovalAssignRequest(approver.getUserId());
        mockMvc.perform(post("/api/v1/documents/{docId}/approval/assign", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignRequest)))
                .andExpect(status().isOk());

        ApprovalReviewRequest reviewRequest = new ApprovalReviewRequest(
                ApprovalStatus.APPROVED,
                "승인"
        );
        mockMvc.perform(post("/api/v1/documents/{docId}/approval/review", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(approver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isOk());

        List<AuditActionType> actions = auditLogRepository.findAll().stream()
                .map(AuditLog::getActionType)
                .toList();
        org.assertj.core.api.Assertions.assertThat(actions)
                .contains(AuditActionType.DOCUMENT_APPROVAL_ASSIGNED)
                .contains(AuditActionType.DOCUMENT_APPROVAL_REVIEWED);
    }

    private String bearer(User user) {
        return "Bearer " + tokenProvider.createAccessToken(user);
    }
}
