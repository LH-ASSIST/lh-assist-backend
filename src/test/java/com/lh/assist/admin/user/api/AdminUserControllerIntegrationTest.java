package com.lh.assist.admin.user.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserPosition;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import com.lh.assist.user.domain.repository.UserRepository;
import com.lh.assist.support.TestDataFactory;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("관리자는 전체 사용자 조회가 가능해야 한다")
    void 관리자_전체_조회() throws Exception {
        User admin = userRepository.save(TestDataFactory.userWith(
                "admin-list@lh.com",
                passwordEncoder.encode("Admin123!"),
                "Admin",
                UserRole.ADMIN,
                UserDepartment.PUBLIC_HOUSING_HEADQUARTERS,
                UserPosition.CHIEF,
                UserStatus.ACTIVE,
                true
        ));
        userRepository.save(TestDataFactory.userWith(
                "dept-a@lh.com",
                passwordEncoder.encode("Test1234!"),
                "DeptA",
                UserRole.USER,
                UserDepartment.PUBLIC_HOUSING_HEADQUARTERS,
                UserPosition.MANAGER,
                UserStatus.ACTIVE,
                true
        ));
        userRepository.save(TestDataFactory.userWith(
                "dept-b@lh.com",
                passwordEncoder.encode("Test1234!"),
                "DeptB",
                UserRole.USER,
                UserDepartment.PUBLIC_HOUSING_ELECTRICAL_OFFICE,
                UserPosition.MANAGER,
                UserStatus.SUSPENDED,
                true
        ));

        TokenPair tokens = loginAndGetTokens(admin.getEmail(), "Admin123!");

        String body = mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        org.assertj.core.api.Assertions.assertThat(data.isArray()).isTrue();
        org.assertj.core.api.Assertions.assertThat(data.toString()).contains("admin-list@lh.com");
        org.assertj.core.api.Assertions.assertThat(data.toString()).contains("dept-a@lh.com");
        org.assertj.core.api.Assertions.assertThat(data.toString()).contains("dept-b@lh.com");
    }

    private TokenPair loginAndGetTokens(
            String email,
            String password
    ) throws Exception {
        Map<String, Object> payload = Map.of(
                "email", email,
                "password", password
        );

        String responseBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(responseBody).get("data");
        return new TokenPair(
                data.get("accessToken").asText(),
                data.get("refreshToken").asText()
        );
    }

    private record TokenPair(
            String accessToken,
            String refreshToken
    ) {
    }
}