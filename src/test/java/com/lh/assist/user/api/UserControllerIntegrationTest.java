package com.lh.assist.user.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class UserControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("마이페이지 조회는 로그인 사용자 정보를 반환해야 한다")
    void 마이페이지_조회_성공() throws Exception {
        User user = userRepository.save(User.builder()
                .email("mypage@lh.com")
                .password(passwordEncoder.encode("Test1234!"))
                .name("MyPage")
                .department(UserDepartment.ETC)
                .position(UserPosition.ETC)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .attemptCount(0)
                .build());

        TokenPair tokens = loginAndGetTokens(user.getEmail(), "Test1234!");

        mockMvc.perform(get("/api/v1/user/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(user.getUserId()))
                .andExpect(jsonPath("$.data.email").value("mypage@lh.com"))
                .andExpect(jsonPath("$.data.name").value("MyPage"));
    }

    @Test
    @DisplayName("비밀번호 변경 후 새 비밀번호로 로그인할 수 있어야 한다")
    void 비밀번호_변경_성공() throws Exception {
        User user = userRepository.save(User.builder()
                .email("pwchange@lh.com")
                .password(passwordEncoder.encode("Old1234!"))
                .name("PasswordChange")
                .department(UserDepartment.ETC)
                .position(UserPosition.ETC)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .attemptCount(0)
                .build());

        TokenPair tokens = loginAndGetTokens(user.getEmail(), "Old1234!");

        Map<String, Object> payload = Map.of(
                "currentPassword", "Old1234!",
                "newPassword", "New1234!"
        );

        mockMvc.perform(put("/api/v1/user/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());

        Map<String, Object> oldLogin = Map.of(
                "email", user.getEmail(),
                "password", "Old1234!"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLogin)))
                .andExpect(status().isUnauthorized());

        Map<String, Object> newLogin = Map.of(
                "email", user.getEmail(),
                "password", "New1234!"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
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

        return extractTokens(responseBody);
    }

    private TokenPair extractTokens(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.get("data");
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