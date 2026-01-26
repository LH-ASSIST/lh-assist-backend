package com.lh.assist.document.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lh.assist.common.security.jwt.JwtTokenProvider;
import com.lh.assist.regulation.domain.enums.domain.entity.Document;
import com.lh.assist.regulation.domain.enums.domain.repository.DocumentRepository;
import com.lh.assist.regulation.domain.enums.domain.enums.DocumentType;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @AfterEach
    void clearData() {
        documentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("지원하지 않는 확장자 업로드 시 400이 반환되어야 한다")
    void 업로드_확장자_검증() throws Exception {
        User user = userRepository.save(TestDataFactory.user("uploader@lh.com"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invalid.txt",
                "text/plain",
                "data".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/documents")
                .file(file)
                .param("docType", DocumentType.NOTICE.name())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("다른 사용자의 문서는 조회할 수 없어야 한다")
    void 다른_사용자_문서_조회_거부() throws Exception {
        User owner = userRepository.save(TestDataFactory.user("owner@lh.com"));
        User other = userRepository.save(TestDataFactory.user("other@lh.com"));
        Document document = documentRepository.save(Document.builder()
                .title("문서")
                .docType(DocumentType.NOTICE)
                .s3Key("documents/1/key.pdf")
                .baseDate(LocalDate.now())
                .user(owner)
                .build());

        mockMvc.perform(get("/api/v1/documents/{docId}", document.getDocId())
                .header(HttpHeaders.AUTHORIZATION, bearer(other)))
            .andExpect(status().isForbidden());
    }

    private String bearer(User user) {
        return "Bearer " + tokenProvider.createToken(user);
    }
}
