package com.lh.assist.analysis.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.analysis.domain.entity.AnalysisJob;
import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.enums.AnalysisJobStatus;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.analysis.domain.repository.AnalysisJobRepository;
import com.lh.assist.analysis.domain.repository.AnalysisResultRepository;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.AnalysisStatus;
import com.lh.assist.document.domain.enums.DocumentType;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.analysis.callback-token=test-token")
class AnalysisCallbackControllerIntegrationTest extends IntegrationTestBase {

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
    private AnalysisJobRepository analysisJobRepository;

    @AfterEach
    void clearData() {
        analysisJobRepository.deleteAll();
        analysisResultRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("분석 완료 콜백은 상태와 점수를 갱신해야 한다")
    void 분석_완료_콜백() throws Exception {
        User user = userRepository.save(TestDataFactory.user("callback@lh.com"));
        Document document = documentRepository.save(Document.builder()
                .title("문서")
                .docType(DocumentType.NOTICE)
                .s3Key("documents/1/sample.pdf")
                .baseDate(LocalDate.now())
                .user(user)
                .build());

        AnalysisResult result = analysisResultRepository.save(AnalysisResult.builder()
                .document(document)
                .baseDate(document.getBaseDate())
                .status(AnalysisResultStatus.REQUESTED)
                .build());

        AnalysisJob job = analysisJobRepository.save(AnalysisJob.builder()
                .analysisResult(result)
                .document(document)
                .baseDate(document.getBaseDate())
                .status(AnalysisJobStatus.REQUESTED)
                .retryCount(0)
                .requestedBy(user)
                .build());

        String payload = objectMapper.writeValueAsString(Map.of(
                "status", "SUCCEEDED",
                "totalRiskScore", 77
        ));

        mockMvc.perform(post("/api/v1/analysis/jobs/{jobId}/callback", job.getJobId())
                .header("X-Analysis-Callback-Token", "test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        AnalysisJob updatedJob = analysisJobRepository.findById(job.getJobId()).orElseThrow();
        AnalysisResult updatedResult = analysisResultRepository.findById(result.getAnalysisId()).orElseThrow();
        Document updatedDocument = documentRepository.findById(document.getDocId()).orElseThrow();

        org.junit.jupiter.api.Assertions.assertEquals(AnalysisJobStatus.SUCCEEDED, updatedJob.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(AnalysisResultStatus.SUCCEEDED, updatedResult.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(77, updatedResult.getTotalRiskScore());
        org.junit.jupiter.api.Assertions.assertEquals(AnalysisStatus.COMPLETED, updatedDocument.getAnalysisStatus());
    }
}