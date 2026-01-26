package com.lh.assist.suggestion.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.Optional;
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

    @AfterEach
    void clearData() {
        suggestionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("목록 조회는 공개 글과 본인 비공개 글만 포함해야 한다")
    void 목록_조회_공개와_본인_비공개만() throws Exception {
        User owner = userRepository.save(TestDataFactory.user("owner@lh.com"));
        User other = userRepository.save(TestDataFactory.user("other@lh.com"));
        suggestionRepository.save(TestDataFactory.suggestion(owner, false));
        suggestionRepository.save(TestDataFactory.suggestion(owner, true));
        suggestionRepository.save(TestDataFactory.suggestion(other, true));

        mockMvc.perform(get("/api/v1/qna")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("비로그인 사용자는 공개 글만 조회할 수 있어야 한다")
    void 비로그인_공개글만_조회() throws Exception {
        User owner = userRepository.save(TestDataFactory.user("owner@lh.com"));
        User other = userRepository.save(TestDataFactory.user("other@lh.com"));
        suggestionRepository.save(TestDataFactory.suggestion(owner, false));
        suggestionRepository.save(TestDataFactory.suggestion(owner, true));
        suggestionRepository.save(TestDataFactory.suggestion(other, true));

        mockMvc.perform(get("/api/v1/qna"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    @DisplayName("비공개 글은 작성자와 관리자만 조회할 수 있어야 한다")
    void 비공개_글_조회_권한() throws Exception {
        User owner = userRepository.save(TestDataFactory.user("owner@lh.com"));
        User other = userRepository.save(TestDataFactory.user("other@lh.com"));
        User admin = userRepository.save(TestDataFactory.admin("admin@lh.com"));
        Suggestion suggestion = suggestionRepository.save(TestDataFactory.suggestion(owner, true));

        mockMvc.perform(get("/api/v1/qna/{id}", suggestion.getSuggestionId())
                .header(HttpHeaders.AUTHORIZATION, bearer(other)))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/qna/{id}", suggestion.getSuggestionId())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.suggestionId").value(suggestion.getSuggestionId()));

        mockMvc.perform(get("/api/v1/qna/{id}", suggestion.getSuggestionId())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.suggestionId").value(suggestion.getSuggestionId()));
    }

    @Test
    @DisplayName("익명 작성 시 작성자 표시는 익명으로 반환되어야 한다")
    void 익명_작성_시_표시명_익명() throws Exception {
        User user = userRepository.save(TestDataFactory.user("anon@lh.com"));

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
        User user = userRepository.save(TestDataFactory.user("writer@lh.com"));

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
            .andExpect(jsonPath("$.data.writerDisplay").value("공공주택사업처 사원"))
            .andExpect(jsonPath("$.data.isPrivate").value(true));
    }

    @Test
    @DisplayName("필수값이 없으면 400이 반환되어야 한다")
    void 작성_필수값_누락시_400() throws Exception {
        User user = userRepository.save(TestDataFactory.user("invalid@lh.com"));

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

    @Test
    @DisplayName("작성자는 건의사항을 수정할 수 있어야 한다")
    void 작성자_수정_성공() throws Exception {
        User owner = userRepository.save(TestDataFactory.user("owner@lh.com"));
        Suggestion suggestion = suggestionRepository.save(TestDataFactory.suggestion(owner, false));

        Map<String, Object> payload = Map.of(
            "title", "수정 제목",
            "content", "수정 내용",
            "category", "RECOMMENDATION",
            "isPrivate", true,
            "isAnonymous", false
        );

        mockMvc.perform(put("/api/v1/qna/{id}", suggestion.getSuggestionId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("수정 제목"))
            .andExpect(jsonPath("$.data.isPrivate").value(true))
            .andExpect(jsonPath("$.data.isAnonymous").value(false));
    }

    @Test
    @DisplayName("작성자가 아니면 건의사항 수정이 거부되어야 한다")
    void 작성자_아니면_수정_거부() throws Exception {
        User owner = userRepository.save(TestDataFactory.user("owner2@lh.com"));
        User other = userRepository.save(TestDataFactory.user("other2@lh.com"));
        Suggestion suggestion = suggestionRepository.save(TestDataFactory.suggestion(owner, false));

        Map<String, Object> payload = Map.of(
            "title", "수정 제목",
            "content", "수정 내용",
            "category", "RECOMMENDATION",
            "isPrivate", true,
            "isAnonymous", false
        );

        mockMvc.perform(put("/api/v1/qna/{id}", suggestion.getSuggestionId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .header(HttpHeaders.AUTHORIZATION, bearer(other)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("작성자는 건의사항을 삭제할 수 있어야 한다")
    void 작성자_삭제_성공() throws Exception {
        User owner = userRepository.save(TestDataFactory.user("owner3@lh.com"));
        Suggestion suggestion = suggestionRepository.save(TestDataFactory.suggestion(owner, false));

        mockMvc.perform(delete("/api/v1/qna/{id}", suggestion.getSuggestionId())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isNoContent());

        Optional<Suggestion> deleted = suggestionRepository.findById(suggestion.getSuggestionId());
        org.assertj.core.api.Assertions.assertThat(deleted).isEmpty();
    }

    private String bearer(User user) {
        return "Bearer " + tokenProvider.createAccessToken(user);
    }

}