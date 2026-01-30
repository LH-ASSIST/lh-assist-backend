package com.lh.assist.notice.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.common.security.jwt.JwtTokenProvider;
import com.lh.assist.notice.domain.entity.Notice;
import com.lh.assist.notice.domain.repository.NoticeRepository;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
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
class NoticeControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @AfterEach
    void clearData() {
        noticeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("공지사항 목록 조회는 비로그인 사용자에게도 반환되어야 한다")
    void 목록_조회_비로그인_성공() throws Exception {
        noticeRepository.save(TestDataFactory.notice("공지 1", "내용 1"));
        noticeRepository.save(TestDataFactory.notice("공지 2", "내용 2"));

        mockMvc.perform(get("/api/v1/notice/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("공지사항 목록 조회는 로그인 사용자에게도 반환되어야 한다")
    void 목록_조회_로그인_성공() throws Exception {
        User user = userRepository.save(TestDataFactory.user("user@lh.com"));
        noticeRepository.save(TestDataFactory.notice("공지 1", "내용 1"));
        noticeRepository.save(TestDataFactory.notice("공지 2", "내용 2"));

        mockMvc.perform(get("/api/v1/notice/all")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("공지사항 상세 조회는 인증이 필요해야 한다")
    void 상세_조회_인증_필요() throws Exception {
        Notice notice = noticeRepository.save(TestDataFactory.notice("공지", "내용"));

        mockMvc.perform(get("/api/v1/notice/{id}", notice.getNoticeId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("공지사항 상세 조회는 로그인 사용자에게 반환되어야 한다")
    void 상세_조회_로그인_성공() throws Exception {
        User user = userRepository.save(TestDataFactory.user("user@lh.com"));
        Notice notice = noticeRepository.save(TestDataFactory.notice("공지", "내용"));

        mockMvc.perform(get("/api/v1/notice/{id}", notice.getNoticeId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.noticeId").value(notice.getNoticeId()));
    }

    @Test
    @DisplayName("공지사항 작성은 관리자만 가능해야 한다")
    void 공지사항_작성_관리자만() throws Exception {
        User user = userRepository.save(TestDataFactory.user("user@lh.com"));

        Map<String, Object> payload = Map.of(
            "title", "공지 제목",
            "content", "공지 내용"
        );

        mockMvc.perform(post("/api/v1/notice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자는 공지사항을 작성할 수 있어야 한다")
    void 공지사항_작성_성공() throws Exception {
        User admin = userRepository.save(TestDataFactory.admin("admin@lh.com"));

        Map<String, Object> payload = Map.of(
            "title", "공지 제목",
            "content", "공지 내용"
        );

        mockMvc.perform(post("/api/v1/notice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("공지 제목"));
    }

    @Test
    @DisplayName("관리자는 공지사항을 수정할 수 있어야 한다")
    void 공지사항_수정_성공() throws Exception {
        User admin = userRepository.save(TestDataFactory.admin("admin@lh.com"));
        Notice notice = noticeRepository.save(TestDataFactory.notice("공지", "내용"));

        Map<String, Object> payload = Map.of(
            "title", "수정 제목",
            "content", "수정 내용"
        );

        mockMvc.perform(put("/api/v1/notice/{id}", notice.getNoticeId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("수정 제목"));
    }

    @Test
    @DisplayName("관리자는 공지사항을 삭제할 수 있어야 한다")
    void 공지사항_삭제_성공() throws Exception {
        User admin = userRepository.save(TestDataFactory.admin("admin@lh.com"));
        Notice notice = noticeRepository.save(TestDataFactory.notice("공지", "내용"));

        mockMvc.perform(delete("/api/v1/notice/{id}", notice.getNoticeId())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
            .andExpect(status().isNoContent());

        Optional<Notice> deleted = noticeRepository.findById(notice.getNoticeId());
        org.assertj.core.api.Assertions.assertThat(deleted).isEmpty();
    }

    @Test
    @DisplayName("공지사항 검색은 비로그인 사용자에게도 반환되어야 한다")
    void 공지사항_검색_비로그인_성공() throws Exception {
        noticeRepository.save(TestDataFactory.notice("점검 안내", "내용 1"));
        noticeRepository.save(TestDataFactory.notice("기타", "점검 일정"));

        mockMvc.perform(get("/api/v1/notice/search")
                .param("type", "TITLE")
                .param("keyword", "점검"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    private String bearer(User user) {
        return "Bearer " + tokenProvider.createAccessToken(user);
    }
}
