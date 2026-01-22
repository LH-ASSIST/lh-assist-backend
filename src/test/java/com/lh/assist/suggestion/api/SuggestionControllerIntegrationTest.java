package com.lh.assist.suggestion.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.common.security.jwt.JwtTokenProvider;
import com.lh.assist.suggestion.domain.SuggestionRepository;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.User;
import com.lh.assist.user.domain.UserRepository;
import com.lh.assist.user.domain.UserRole;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SuggestionControllerIntegrationTest extends IntegrationTestBase {

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

    @Test
    @DisplayName("목록 조회는 공개 글과 본인 비공개 글만 포함해야 한다")
    void 목록_조회_공개와_본인_비공개만() throws Exception {
        User owner = userRepository.save(TestDataFactory.user("owner@lh.com", UserRole.USER));
        User other = userRepository.save(TestDataFactory.user("other@lh.com", UserRole.USER));
        suggestionRepository.save(TestDataFactory.suggestion(owner, false));
        suggestionRepository.save(TestDataFactory.suggestion(owner, true));
        suggestionRepository.save(TestDataFactory.suggestion(other, true));

        mockMvc.perform(get("/api/v1/qna")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("비공개 글은 작성자와 관리자만 조회할 수 있어야 한다")
    void 비공개_글_조회_권한() throws Exception {
        User owner = userRepository.save(TestDataFactory.user("owner@lh.com", UserRole.USER));
        User other = userRepository.save(TestDataFactory.user("other@lh.com", UserRole.USER));
        Suggestion suggestion = suggestionRepository.save(TestDataFactory.suggestion(owner, true));

        mockMvc.perform(get("/api/v1/qna/{id}", suggestion.getSuggestionId())
                .header(HttpHeaders.AUTHORIZATION, bearer(other)))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/qna/{id}", suggestion.getSuggestionId())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.suggestionId").value(suggestion.getSuggestionId()));
    }

    @Test
    @DisplayName("익명 작성 시 작성자 표시는 익명으로 반환되어야 한다")
    void 익명_작성_시_표시명_익명() throws Exception {
        User user = userRepository.save(TestDataFactory.user("anon@lh.com", UserRole.USER));

        Map<String, Object> payload = Map.of(
            "title", "제목",
            "content", "내용",
            "category", "SYSTEM_ERROR",
            "isPrivate", false,
            "isAnonymous", true
        );

        mockMvc.perform(post("/api/v1/qna")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.writerDisplay").value("익명"))
            .andExpect(jsonPath("$.data.userId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("비익명 작성 시 부서와 직급이 표시되어야 한다")
    void 비익명_작성_시_부서_직급_표시() throws Exception {
        User user = userRepository.save(TestDataFactory.user("writer@lh.com", UserRole.USER));

        Map<String, Object> payload = Map.of(
            "title", "제목",
            "content", "내용",
            "category", "SYSTEM_ERROR",
            "isPrivate", true,
            "isAnonymous", false
        );

        mockMvc.perform(post("/api/v1/qna")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.writerDisplay").value("공공주택본부 차장"))
            .andExpect(jsonPath("$.data.isPrivate").value(true));
    }

    @Test
    @DisplayName("필수값이 없으면 400이 반환되어야 한다")
    void 작성_필수값_누락시_400() throws Exception {
        User user = userRepository.save(TestDataFactory.user("invalid@lh.com", UserRole.USER));

        Map<String, Object> payload = Map.of(
            "content", "내용",
            "category", "SYSTEM_ERROR",
            "isPrivate", false,
            "isAnonymous", false
        );

        mockMvc.perform(post("/api/v1/qna")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isBadRequest());
    }

    private String bearer(User user) {
        return "Bearer " + tokenProvider.createToken(user);
    }

}