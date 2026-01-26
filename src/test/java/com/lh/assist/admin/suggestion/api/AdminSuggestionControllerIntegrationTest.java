package com.lh.assist.admin.suggestion.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lh.assist.common.security.jwt.JwtTokenProvider;
import com.lh.assist.suggestion.domain.repository.SuggestionRepository;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import com.lh.assist.user.domain.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
        User admin = userRepository.save(TestDataFactory.user("admin@lh.com", UserRole.ADMIN));
        User user = userRepository.save(TestDataFactory.user("user@lh.com", UserRole.USER));
        suggestionRepository.save(TestDataFactory.suggestion(user, false));
        suggestionRepository.save(TestDataFactory.suggestion(user, true));

        mockMvc.perform(get("/api/v1/admin/suggestions")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    private String bearer(User user) {
        return "Bearer " + tokenProvider.createToken(user);
    }

}