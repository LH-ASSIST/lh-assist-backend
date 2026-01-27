package com.lh.assist.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.lh.assist.support.IntegrationTestBase;
import java.util.Map;

import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserPosition;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import com.lh.assist.user.domain.repository.UserRepository;
import com.lh.assist.suggestion.domain.entity.Suggestion;
import com.lh.assist.suggestion.domain.repository.SuggestionRepository;
import com.lh.assist.support.TestDataFactory;
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
class AuthControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SuggestionRepository suggestionRepository;

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
                "department", UserDepartment.ETC,
                "position", UserPosition.ETC
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
                .department(UserDepartment.ETC)
                .position(UserPosition.ETC)
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
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.email").value("tester2@lh.com"));
    }

    @Test
    @DisplayName("리프레시 토큰으로 재발급 시 새로운 토큰이 발급되어야 한다")
    void 리프레시_재발급_성공() throws Exception {
        User user = userRepository.save(User.builder()
                .email("refresh1@lh.com")
                .password(passwordEncoder.encode("Test1234!"))
                .name("RefreshTester")
                .department(UserDepartment.ETC)
                .position(UserPosition.ETC)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .attemptCount(0)
                .build());

        TokenPair tokens = loginAndGetTokens(user.getEmail(), "Test1234!");

        Map<String, Object> payload = Map.of("refreshToken", tokens.refreshToken());

        String responseBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        TokenPair rotated = extractTokens(responseBody);
        org.assertj.core.api.Assertions.assertThat(rotated.refreshToken()).isNotEqualTo(tokens.refreshToken());
    }

    @Test
    @DisplayName("폐기된 리프레시 토큰 재사용 시 인증이 거부되어야 한다")
    void 리프레시_재사용_차단() throws Exception {
        User user = userRepository.save(User.builder()
                .email("refresh2@lh.com")
                .password(passwordEncoder.encode("Test1234!"))
                .name("RefreshReuseTester")
                .department(UserDepartment.ETC)
                .position(UserPosition.ETC)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .attemptCount(0)
                .build());

        TokenPair tokens = loginAndGetTokens(user.getEmail(), "Test1234!");

        Map<String, Object> payload = Map.of("refreshToken", tokens.refreshToken());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃 후 리프레시 토큰이 더 이상 유효하지 않아야 한다")
    void 로그아웃_후_리프레시_무효() throws Exception {
        User user = userRepository.save(User.builder()
                .email("logout1@lh.com")
                .password(passwordEncoder.encode("Test1234!"))
                .name("LogoutTester")
                .department(UserDepartment.ETC)
                .position(UserPosition.ETC)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .attemptCount(0)
                .build());

        TokenPair tokens = loginAndGetTokens(user.getEmail(), "Test1234!");

        Map<String, Object> logoutPayload = Map.of("refreshToken", tokens.refreshToken());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutPayload))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());

        Map<String, Object> refreshPayload = Map.of("refreshToken", tokens.refreshToken());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshPayload)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃된 액세스 토큰으로 보호된 API에 접근하면 거부되어야 한다")
    void 로그아웃_후_액세스_토큰_차단() throws Exception {
        User user = userRepository.save(User.builder()
                .email("logout2@lh.com")
                .password(passwordEncoder.encode("Test1234!"))
                .name("LogoutAccessTester")
                .department(UserDepartment.ETC)
                .position(UserPosition.ETC)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .attemptCount(0)
                .build());

        Suggestion suggestion = suggestionRepository.save(
                TestDataFactory.suggestion(user, false)
        );

        TokenPair tokens = loginAndGetTokens(user.getEmail(), "Test1234!");

        Map<String, Object> logoutPayload = Map.of("refreshToken", tokens.refreshToken());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutPayload))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/qna/{id}", suggestion.getSuggestionId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isForbidden());
    }

    private TokenPair loginAndGetTokens(String email, String password) throws Exception {
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

    private record TokenPair(String accessToken, String refreshToken) {
    }
}
