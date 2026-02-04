package com.lh.assist.user.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.LhAssistBackendApplication;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserPosition;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import com.lh.assist.user.domain.repository.UserRepository;
import com.lh.assist.support.TestDataFactory;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = LhAssistBackendApplication.class)
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

    @MockBean
    private JavaMailSender mailSender;

    @Test
    @DisplayName("마이페이지 조회는 로그인 사용자 정보를 반환해야 한다")
    void 마이페이지_조회_성공() throws Exception {
        User user = userRepository.save(TestDataFactory.userWith(
                "mypage@lh.com",
                passwordEncoder.encode("Test1234!"),
                "MyPage",
                UserRole.USER,
                UserDepartment.ETC,
                UserPosition.ETC,
                UserStatus.ACTIVE,
                true
        ));

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
        User user = userRepository.save(TestDataFactory.userWith(
                "pwchange@lh.com",
                passwordEncoder.encode("Old1234!"),
                "PasswordChange",
                UserRole.USER,
                UserDepartment.ETC,
                UserPosition.ETC,
                UserStatus.ACTIVE,
                true
        ));

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

    @Test
    @DisplayName("비밀번호 찾기 시 임시 비밀번호로 로그인할 수 있어야 한다")
    void 비밀번호_찾기_임시_비밀번호_로그인() throws Exception {
        User user = userRepository.save(TestDataFactory.userWith(
                "reset@lh.com",
                passwordEncoder.encode("Old1234!"),
                "Reset",
                UserRole.USER,
                UserDepartment.ETC,
                UserPosition.ETC,
                UserStatus.ACTIVE,
                true
        ));

        Map<String, Object> payload = Map.of("email", user.getEmail());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        org.mockito.Mockito.when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        mockMvc.perform(post("/api/v1/user/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        String tempPassword = extractTempPassword(extractBody(message));

        Map<String, Object> loginPayload = Map.of(
                "email", user.getEmail(),
                "password", tempPassword
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("일반 사용자는 요청 부서와 무관하게 본인 부서만 조회되어야 한다")
    void 일반_사용자_부서_제한() throws Exception {
        User user = userRepository.save(TestDataFactory.userWith(
                "user-list@lh.com",
                passwordEncoder.encode("User123!"),
                "User",
                UserRole.USER,
                UserDepartment.PUBLIC_HOUSING_HEADQUARTERS,
                UserPosition.STAFF,
                UserStatus.ACTIVE,
                true
        ));
        userRepository.save(TestDataFactory.userWith(
                "other-dept@lh.com",
                passwordEncoder.encode("Test1234!"),
                "Other",
                UserRole.USER,
                UserDepartment.PUBLIC_HOUSING_ELECTRICAL_OFFICE,
                UserPosition.STAFF,
                UserStatus.ACTIVE,
                true
        ));

        TokenPair tokens = loginAndGetTokens(user.getEmail(), "User123!");

        String body = mockMvc.perform(get("/api/v1/user/department")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.get("data");
        org.assertj.core.api.Assertions.assertThat(data.toString()).contains("user-list@lh.com");
        org.assertj.core.api.Assertions.assertThat(data.toString()).doesNotContain("other-dept@lh.com");
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

    private String extractTempPassword(String text) {
        int label = text.indexOf("임시 비밀번호:");
        if (label < 0) {
            return "";
        }
        int spanStart = text.indexOf("<span", label);
        if (spanStart < 0) {
            return "";
        }
        int valueStart = text.indexOf(">", spanStart);
        int valueEnd = text.indexOf("</span>", valueStart);
        if (valueStart < 0 || valueEnd < 0) {
            return "";
        }
        return text.substring(valueStart + 1, valueEnd).trim();
    }

    private String extractBody(MimeMessage message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            BodyPart part = multipart.getBodyPart(0);
            Object partContent = part.getContent();
            return partContent instanceof String ? (String) partContent : partContent.toString();
        }
        return content != null ? content.toString() : "";
    }

    private record TokenPair(
            String accessToken,
            String refreshToken
    ) {
    }
}