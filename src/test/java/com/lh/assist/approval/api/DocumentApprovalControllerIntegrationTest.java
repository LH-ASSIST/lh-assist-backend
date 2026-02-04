package com.lh.assist.approval.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.approval.domain.repository.DocumentApprovalRepository;
import com.lh.assist.common.security.jwt.JwtTokenProvider;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.ApprovalStatus;
import com.lh.assist.document.domain.enums.DocumentType;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.hamcrest.Matchers;
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
class DocumentApprovalControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentApprovalRepository approvalRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @AfterEach
    void clearData() {
        approvalRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("관리자는 상위권자를 지정할 수 있어야 한다")
    void 상위권자_지정_관리자_성공() throws Exception {
        User admin = userRepository.save(TestDataFactory.admin("approval-admin@lh.com"));
        User approver = userRepository.save(TestDataFactory.user("approval-approver@lh.com"));
        User owner = userRepository.save(TestDataFactory.user("approval-owner@lh.com"));

        Document document = documentRepository.save(Document.builder()
                .title("문서")
                .docType(DocumentType.PLAN)
                .s3Key("documents/1/key.pdf")
                .baseDate(LocalDate.now())
                .user(owner)
                .build());

        Map<String, Object> payload = Map.of("approverId", approver.getUserId());

        mockMvc.perform(post("/api/v1/documents/{docId}/approval/assign", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value(ApprovalStatus.WAITING.name()))
                .andExpect(jsonPath("$.data.reviewerName").value(approver.getName()));
    }

    @Test
    @DisplayName("문서 소유자는 상위권자를 지정할 수 있어야 한다")
    void 상위권자_지정_소유자_성공() throws Exception {
        User approver = userRepository.save(TestDataFactory.user("approval-approver-owner@lh.com"));
        User owner = userRepository.save(TestDataFactory.user("approval-owner-owner@lh.com"));

        Document document = documentRepository.save(Document.builder()
                .title("문서")
                .docType(DocumentType.PLAN)
                .s3Key("documents/1/key.pdf")
                .baseDate(LocalDate.now())
                .user(owner)
                .build());

        Map<String, Object> payload = Map.of("approverId", approver.getUserId());

        mockMvc.perform(post("/api/v1/documents/{docId}/approval/assign", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value(ApprovalStatus.WAITING.name()))
                .andExpect(jsonPath("$.data.reviewerName").value(approver.getName()));
    }

    @Test
    @DisplayName("지정된 상위권자가 승인 처리를 할 수 있어야 한다")
    void 승인_처리_성공() throws Exception {
        User admin = userRepository.save(TestDataFactory.admin("approval-admin2@lh.com"));
        User approver = userRepository.save(TestDataFactory.user("approval-approver2@lh.com"));
        User owner = userRepository.save(TestDataFactory.user("approval-owner2@lh.com"));

        Document document = documentRepository.save(Document.builder()
                .title("문서")
                .docType(DocumentType.PLAN)
                .s3Key("documents/2/key.pdf")
                .baseDate(LocalDate.now())
                .user(owner)
                .build());

        Map<String, Object> assignPayload = Map.of("approverId", approver.getUserId());
        mockMvc.perform(post("/api/v1/documents/{docId}/approval/assign", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignPayload)))
                .andExpect(status().isOk());

        Map<String, Object> reviewPayload = Map.of(
                "status", ApprovalStatus.APPROVED.name(),
                "reviewComment", "승인합니다."
        );

        mockMvc.perform(post("/api/v1/documents/{docId}/approval/review", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(approver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value(ApprovalStatus.APPROVED.name()))
                .andExpect(jsonPath("$.data.reviewComment").value("승인합니다."));
    }

    @Test
    @DisplayName("지정된 상위권자가 아니면 승인 처리가 거부되어야 한다")
    void 승인_처리_권한_거부() throws Exception {
        User admin = userRepository.save(TestDataFactory.admin("approval-admin3@lh.com"));
        User approver = userRepository.save(TestDataFactory.user("approval-approver3@lh.com"));
        User other = userRepository.save(TestDataFactory.user("approval-other@lh.com"));
        User owner = userRepository.save(TestDataFactory.user("approval-owner3@lh.com"));

        Document document = documentRepository.save(Document.builder()
                .title("문서")
                .docType(DocumentType.PLAN)
                .s3Key("documents/3/key.pdf")
                .baseDate(LocalDate.now())
                .user(owner)
                .build());

        Map<String, Object> assignPayload = Map.of("approverId", approver.getUserId());
        mockMvc.perform(post("/api/v1/documents/{docId}/approval/assign", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignPayload)))
                .andExpect(status().isOk());

        Map<String, Object> reviewPayload = Map.of(
                "status", ApprovalStatus.REJECTED.name(),
                "reviewComment", "반려합니다."
        );

        mockMvc.perform(post("/api/v1/documents/{docId}/approval/review", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewPayload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("문서 소유자는 승인 상세를 조회할 수 있어야 한다")
    void 승인_상세_조회_성공() throws Exception {
        User admin = userRepository.save(TestDataFactory.admin("approval-admin4@lh.com"));
        User approver = userRepository.save(TestDataFactory.user("approval-approver4@lh.com"));
        User owner = userRepository.save(TestDataFactory.user("approval-owner4@lh.com"));

        Document document = documentRepository.save(Document.builder()
                .title("문서")
                .docType(DocumentType.PLAN)
                .s3Key("documents/4/key.pdf")
                .baseDate(LocalDate.now())
                .user(owner)
                .build());

        Map<String, Object> assignPayload = Map.of("approverId", approver.getUserId());
        mockMvc.perform(post("/api/v1/documents/{docId}/approval/assign", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignPayload)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/documents/{docId}/approval", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value(ApprovalStatus.WAITING.name()))
                .andExpect(jsonPath("$.data.reviewerName").value(approver.getName()))
                .andExpect(jsonPath("$.data.approverCandidates.length()").value(1))
                .andExpect(jsonPath("$.data.approverCandidates[*].userId", Matchers.hasItem(approver.getUserId().intValue())));
    }

    private String bearer(User user) {
        return "Bearer " + tokenProvider.createAccessToken(user);
    }
}