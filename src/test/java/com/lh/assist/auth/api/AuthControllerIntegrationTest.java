package com.lh.assist.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.support.IntegrationTestBase;
import java.util.Map;

import com.lh.assist.user.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("회원가입 필수값이 누락되면 400이 반환되어야 한다")
    void 회원가입_필수값_누락() throws Exception {
        Map<String, Object> payload = Map.of(
                "password", "Test1234!",
                "name", "Tester"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그인 필수값이 누락되면 400이 반환되어야 한다")
    void 로그인_필수값_누락() throws Exception {
        Map<String, Object> payload = Map.of(
                "email", "user@lh.com"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회원가입이 성공하면 201을 반환해야 한다")
    void 회원가입_성공하면_201_반환() throws Exception {
        Map<String, Object> payload = Map.of(
                "email", "tester1@lh.com",
                "password", "Test1234!",
                "name", "Tester",
                "department", UserDepartment.PUBLIC_HOUSING_HEADQUARTERS,
                "position", UserPosition.STAFF
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.email").value("tester1@lh.com"))
                .andExpect(jsonPath("$.data.userId").isNumber());
    }

    @Test
    @DisplayName("로그인이 성공하면 토큰을 반환해야 한다")
    void 로그인_성공하면_토큰_반환() throws Exception {
        User user = User.builder()
                .email("tester2@lh.com")
                .password(passwordEncoder.encode("Test1234!"))
                .name("LoginTester")
                .department(UserDepartment.PUBLIC_HOUSING_HEADQUARTERS)
                .position(UserPosition.TEAM_LEAD)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .attemptCount(0)
                .build();
        userRepository.save(user);

        Map<String, Object> payload = Map.of(
                "email", "tester2@lh.com",
                "password", "Test1234!"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.email").value("tester2@lh.com"));
    }
}