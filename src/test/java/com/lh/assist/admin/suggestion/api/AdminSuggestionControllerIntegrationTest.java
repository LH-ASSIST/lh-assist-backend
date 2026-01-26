package com.lh.assist.admin.suggestion.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.common.security.jwt.JwtTokenProvider;
import com.lh.assist.suggestion.domain.entity.Suggestion;
import com.lh.assist.suggestion.domain.repository.SuggestionRepository;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.AfterEach;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminSuggestionControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SuggestionRepository suggestionRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @AfterEach
    void clearData() {
        suggestionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("관리자 전체 조회는 모든 글을 포함해야 한다")
    void 관리자_전체_조회() throws Exception {
        User admin = userRepository.save(TestDataFactory.admin("admin@lh.com"));
        User user = userRepository.save(TestDataFactory.user("user@lh.com"));
        suggestionRepository.save(TestDataFactory.suggestion(user, false));
        suggestionRepository.save(TestDataFactory.suggestion(user, true));

        mockMvc.perform(get("/api/v1/admin/suggestions")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("관리자는 건의사항에 답변을 등록할 수 있어야 한다")
    void 관리자_답변_등록() throws Exception {
        User admin = userRepository.save(TestDataFactory.admin("admin2@lh.com"));
        User user = userRepository.save(TestDataFactory.user("user2@lh.com"));
        Suggestion suggestion = suggestionRepository.save(TestDataFactory.suggestion(user, false));

        Map<String, Object> payload = Map.of("answerContent", "답변 내용");

        mockMvc.perform(patch("/api/v1/admin/suggestions/{id}/answer", suggestion.getSuggestionId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ANSWERED"))
            .andExpect(jsonPath("$.data.answerContent").value("답변 내용"));
    }

    @Test
    @DisplayName("관리자가 아니면 답변 등록이 거부되어야 한다")
    void 관리자_아니면_답변_거부() throws Exception {
        User user = userRepository.save(TestDataFactory.user("user3@lh.com"));
        Suggestion suggestion = suggestionRepository.save(TestDataFactory.suggestion(user, false));

        Map<String, Object> payload = Map.of("answerContent", "답변 내용");

        mockMvc.perform(patch("/api/v1/admin/suggestions/{id}/answer", suggestion.getSuggestionId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isForbidden());
    }

    private String bearer(User user) {
        return "Bearer " + tokenProvider.createToken(user);
    }

}