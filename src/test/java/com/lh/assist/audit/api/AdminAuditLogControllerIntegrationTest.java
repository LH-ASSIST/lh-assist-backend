package com.lh.assist.audit.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lh.assist.audit.domain.entity.AuditLog;
import com.lh.assist.audit.domain.enums.AuditActionType;
import com.lh.assist.audit.domain.enums.AuditTargetType;
import com.lh.assist.audit.domain.repository.AuditLogRepository;
import com.lh.assist.common.security.jwt.JwtTokenProvider;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAuditLogControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @AfterEach
    void cleanup() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("관리자는 감사 로그를 필터링하여 조회할 수 있어야 한다")
    void 관리자_감사로그_조회() throws Exception {
        User admin = userRepository.save(TestDataFactory.admin("admin-audit@lh.com"));
        User actor = userRepository.save(TestDataFactory.user("actor@lh.com"));

        auditLogRepository.save(AuditLog.builder()
                .actionType(AuditActionType.NOTICE_CREATED)
                .targetType(AuditTargetType.NOTICE)
                .targetId(1L)
                .s3Key("noticeId:1")
                .actor(actor)
                .build());
        auditLogRepository.save(AuditLog.builder()
                .actionType(AuditActionType.DOCUMENT_UPLOAD)
                .targetType(AuditTargetType.DOCUMENT)
                .targetId(2L)
                .s3Key("documents/2")
                .actor(actor)
                .build());

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("actionType", "NOTICE_CREATED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].actionType").value("NOTICE_CREATED"))
                .andExpect(jsonPath("$.data.content[0].targetType").value("NOTICE"))
                .andExpect(jsonPath("$.data.content[0].actorEmail").value("actor@lh.com"));
    }

    private String bearer(User user) {
        return "Bearer " + tokenProvider.createAccessToken(user);
    }
}