package com.lh.assist.analysis.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.entity.AnalysisSection;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.analysis.domain.repository.AnalysisResultRepository;
import com.lh.assist.analysis.domain.repository.AnalysisSectionRepository;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.DocumentType;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserPosition;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import com.lh.assist.user.domain.repository.UserRepository;
import java.time.LocalDate;
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
class AnalysisControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private AnalysisSectionRepository analysisSectionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("분석 요약 조회는 최신 결과를 반환해야 한다")
    void 분석_요약_조회_성공() throws Exception {
        User user = createUser("summary@lh.com");
        Document document = createDocument(user, "documents/summary.pdf");
        AnalysisResult result = analysisResultRepository.save(AnalysisResult.builder()
                .document(document)
                .baseDate(document.getBaseDate())
                .totalRiskScore(75)
                .status(AnalysisResultStatus.SUCCEEDED)
                .build());

        analysisSectionRepository.save(AnalysisSection.builder()
                .analysisResult(result)
                .externalSectionId("TXT_001_001")
                .bbox("[1,2,3,4]")
                .pageNumber(1)
                .isViolation(true)
                .riskScore(80)
                .reasoning("위반 내용")
                .build());

        analysisSectionRepository.save(AnalysisSection.builder()
                .analysisResult(result)
                .externalSectionId("TXT_001_002")
                .bbox("[5,6,7,8]")
                .pageNumber(2)
                .isViolation(false)
                .riskScore(10)
                .reasoning("정상")
                .build());

        TokenPair tokens = loginAndGetTokens(user.getEmail());

        mockMvc.perform(get("/api/v1/analysis/documents/{docId}/summary", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(result.getAnalysisId()))
                .andExpect(jsonPath("$.data.totalRiskScore").value(75))
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.totalViolations").value(1));
    }

    @Test
    @DisplayName("분석 섹션 조회는 하이라이팅 정보를 반환해야 한다")
    void 분석_섹션_조회_성공() throws Exception {
        User user = createUser("sections@lh.com");
        Document document = createDocument(user, "documents/sections.pdf");
        AnalysisResult result = analysisResultRepository.save(AnalysisResult.builder()
                .document(document)
                .baseDate(document.getBaseDate())
                .totalRiskScore(20)
                .status(AnalysisResultStatus.SUCCEEDED)
                .build());

        AnalysisSection saved = analysisSectionRepository.save(AnalysisSection.builder()
                .analysisResult(result)
                .externalSectionId("TXT_002_001")
                .bbox("[10,20,30,40]")
                .pageNumber(3)
                .isViolation(true)
                .riskScore(55)
                .reasoning("설명")
                .build());

        TokenPair tokens = loginAndGetTokens(user.getEmail());

        mockMvc.perform(get("/api/v1/analysis/documents/{docId}/sections", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sectionId").value(saved.getSectionId()))
                .andExpect(jsonPath("$.data[0].page").value(3))
                .andExpect(jsonPath("$.data[0].bbox").value("[10,20,30,40]"))
                .andExpect(jsonPath("$.data[0].violation").value(true))
                .andExpect(jsonPath("$.data[0].riskScore").value(55))
                .andExpect(jsonPath("$.data[0].reasoning").value("설명"));
    }

    @Test
    @DisplayName("분석 조회는 인증이 없으면 거부되어야 한다")
    void 분석_조회_인증_필요() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/documents/{docId}/summary", 1L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("분석 섹션 조회는 인증이 없으면 거부되어야 한다")
    void 분석_섹션_조회_인증_필요() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/documents/{docId}/sections", 1L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("문서 소유자가 아니면 분석 조회가 거부되어야 한다")
    void 분석_조회_소유자_아님() throws Exception {
        User owner = createUser("owner@lh.com");
        User other = createUser("other@lh.com");
        Document document = createDocument(owner, "documents/owner.pdf");
        analysisResultRepository.save(AnalysisResult.builder()
                .document(document)
                .baseDate(document.getBaseDate())
                .totalRiskScore(10)
                .status(AnalysisResultStatus.SUCCEEDED)
                .build());

        TokenPair tokens = loginAndGetTokens(other.getEmail());

        mockMvc.perform(get("/api/v1/analysis/documents/{docId}/summary", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("문서 소유자가 아니면 분석 섹션 조회가 거부되어야 한다")
    void 분석_섹션_조회_소유자_아님() throws Exception {
        User owner = createUser("owner-section@lh.com");
        User other = createUser("other-section@lh.com");
        Document document = createDocument(owner, "documents/owner-section.pdf");
        analysisResultRepository.save(AnalysisResult.builder()
                .document(document)
                .baseDate(document.getBaseDate())
                .totalRiskScore(10)
                .status(AnalysisResultStatus.SUCCEEDED)
                .build());

        TokenPair tokens = loginAndGetTokens(other.getEmail());

        mockMvc.perform(get("/api/v1/analysis/documents/{docId}/sections", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("성공한 분석 결과가 없으면 404가 반환되어야 한다")
    void 분석_결과_없음() throws Exception {
        User user = createUser("missing@lh.com");
        Document document = createDocument(user, "documents/missing.pdf");
        analysisResultRepository.save(AnalysisResult.builder()
                .document(document)
                .baseDate(document.getBaseDate())
                .totalRiskScore(10)
                .status(AnalysisResultStatus.REQUESTED)
                .build());

        TokenPair tokens = loginAndGetTokens(user.getEmail());

        mockMvc.perform(get("/api/v1/analysis/documents/{docId}/summary", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("성공한 분석 결과가 없으면 섹션 조회는 404가 반환되어야 한다")
    void 분석_섹션_결과_없음() throws Exception {
        User user = createUser("missing-sections@lh.com");
        Document document = createDocument(user, "documents/missing-sections.pdf");
        analysisResultRepository.save(AnalysisResult.builder()
                .document(document)
                .baseDate(document.getBaseDate())
                .totalRiskScore(10)
                .status(AnalysisResultStatus.REQUESTED)
                .build());

        TokenPair tokens = loginAndGetTokens(user.getEmail());

        mockMvc.perform(get("/api/v1/analysis/documents/{docId}/sections", document.getDocId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("문서가 없으면 분석 요약 조회는 404가 반환되어야 한다")
    void 분석_요약_문서_없음() throws Exception {
        User user = createUser("missing-doc@lh.com");
        TokenPair tokens = loginAndGetTokens(user.getEmail());

        mockMvc.perform(get("/api/v1/analysis/documents/{docId}/summary", 9999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("문서가 없으면 분석 섹션 조회는 404가 반환되어야 한다")
    void 분석_섹션_문서_없음() throws Exception {
        User user = createUser("missing-doc-sections@lh.com");
        TokenPair tokens = loginAndGetTokens(user.getEmail());

        mockMvc.perform(get("/api/v1/analysis/documents/{docId}/sections", 9999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isNotFound());
    }

    private User createUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("Test1234!"))
                .name("Tester")
                .department(UserDepartment.ETC)
                .position(UserPosition.ETC)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .attemptCount(0)
                .build());
    }

    private Document createDocument(
            User user,
            String s3Key
    ) {
        return documentRepository.save(Document.builder()
                .title("문서")
                .docType(DocumentType.PLAN)
                .s3Key(s3Key)
                .baseDate(LocalDate.now())
                .user(user)
                .build());
    }

    private TokenPair loginAndGetTokens(String email) throws Exception {
        Map<String, Object> payload = Map.of(
                "email", email,
                "password", "Test1234!"
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