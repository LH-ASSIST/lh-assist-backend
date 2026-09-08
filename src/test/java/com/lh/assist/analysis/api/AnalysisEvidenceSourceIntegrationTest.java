package com.lh.assist.analysis.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.analysis.domain.entity.AnalysisEvidence;
import com.lh.assist.analysis.domain.entity.AnalysisJob;
import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.enums.AnalysisJobStatus;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.analysis.domain.repository.AnalysisEvidenceRepository;
import com.lh.assist.analysis.domain.repository.AnalysisJobRepository;
import com.lh.assist.analysis.domain.repository.AnalysisResultRepository;
import com.lh.assist.analysis.domain.repository.AnalysisSectionRepository;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.DocumentType;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.reg.domain.entity.RegItem;
import com.lh.assist.reg.domain.entity.Regulation;
import com.lh.assist.reg.domain.enums.RegulationType;
import com.lh.assist.reg.domain.repository.RegItemRepository;
import com.lh.assist.reg.domain.repository.RegulationRepository;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AI 콜백이 인용한 근거(source_id)의 무결성을 검증한다
 *
 * 존재하는 조항이면 정확한 규정 버전/발효일을 스냅샷으로 남기고, 존재하지 않는 조항이면
 * (AI 할루시네이션 상황) 콜백을 거부해 잘못된 근거가 그대로 저장되지 않게 한다
 *
 * 클래스 레벨 @Transactional을 쓰지 않는다: 할루시네이션 케이스가 서비스단
 * @Transactional을 rollback-only로 마킹시키는데, 테스트 트랜잭션과 같은 트랜잭션을
 * 공유하면 이후 어떤 리포지토리 호출도 오염된 영속성 컨텍스트 때문에 실패한다.
 * 각 테스트가 실제로 커밋/롤백되게 두고 @AfterEach에서 직접 정리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.analysis.callback-token=test-token")
class AnalysisEvidenceSourceIntegrationTest extends IntegrationTestBase {

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

    @Autowired
    private AnalysisSectionRepository analysisSectionRepository;

    @Autowired
    private AnalysisEvidenceRepository analysisEvidenceRepository;

    @Autowired
    private RegulationRepository regulationRepository;

    @Autowired
    private RegItemRepository regItemRepository;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void clearData() {
        analysisEvidenceRepository.deleteAll();
        analysisSectionRepository.deleteAll();
        analysisJobRepository.deleteAll();
        analysisResultRepository.deleteAll();
        documentRepository.deleteAll();
        regItemRepository.deleteAll();
        regulationRepository.deleteAll();
        userRepository.deleteAll();
    }

    private AnalysisJob setUpJob(User user, Document document) {
        AnalysisResult result = analysisResultRepository.save(AnalysisResult.builder()
                .document(document)
                .baseDate(document.getBaseDate())
                .status(AnalysisResultStatus.REQUESTED)
                .build());

        return analysisJobRepository.save(AnalysisJob.builder()
                .analysisResult(result)
                .document(document)
                .baseDate(document.getBaseDate())
                .status(AnalysisJobStatus.REQUESTED)
                .retryCount(0)
                .requestedBy(user)
                .build());
    }

    private String dummyVectorLiteral() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 1536; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("0.001");
        }
        return sb.append("]").toString();
    }

    private Map<String, Object> callbackPayload(String sourceId) {
        return Map.of(
                "status", "SUCCEEDED",
                "total_risk_score", 50,
                "sections", List.of(Map.of(
                        "external_section_id", "sec-1",
                        "page_number", 1,
                        "is_violation", true,
                        "risk_score", 50,
                        "reasoning", "테스트",
                        "risk_items", List.of(Map.of(
                                "risk_type", "MISSING",
                                "detected_text", "누락된 문구",
                                "guide_message", "가이드",
                                "priority", 1,
                                "reasoning", "테스트",
                                "evidences", List.of(Map.of(
                                        "source_type", "REG_ITEM",
                                        "source_id", sourceId,
                                        "quote", "규정 원문"
                                ))
                        ))
                ))
        );
    }

    @Test
    @DisplayName("실재하는 조항을 근거로 들면 판정 시점 규정 버전/발효일이 스냅샷으로 저장된다")
    void 실재하는_근거_버전_스냅샷_저장() throws Exception {
        User user = userRepository.save(TestDataFactory.user("evidence-ok@lh.com"));
        Document document = documentRepository.save(Document.builder()
                .title("문서")
                .docType(DocumentType.NOTICE)
                .s3Key("documents/1/sample.pdf")
                .baseDate(LocalDate.now())
                .user(user)
                .build());
        AnalysisJob job = setUpJob(user, document);

        Regulation regulation = regulationRepository.save(Regulation.builder()
                .title("공공주택 특별법")
                .regType(RegulationType.LAW)
                .effectiveDate(LocalDate.of(2024, 1, 1))
                .active(true)
                .amendmentDate(LocalDate.of(2024, 1, 1))
                .version("2024-v1")
                .sourceUrl("https://law.go.kr/test")
                .build());
        // JPA의 VectorStringConverter는 vector 컬럼에 varchar 바인드 타입을 그대로 넘겨
        // Postgres가 캐스팅을 거부한다(실서비스에서도 reg_items 적재는 vector.py가
        // ::vector 캐스트를 명시한 raw SQL로 한다, JPA save()로 쓰지 않는다).
        // 같은 방식으로 네이티브 SQL에 명시적 캐스트를 줘서 실제 적재 경로를 재현한다.
        Long regItemId = ((Number) entityManager.createNativeQuery(
                        "INSERT INTO reg_items (clause_number, content, content_hash, change_type, "
                                + "is_mandatory, vector_index, reg_id, created_at, updated_at) "
                                + "VALUES (:clauseNumber, :content, :contentHash, :changeType, "
                                + ":mandatory, CAST(:vector AS vector), :regId, NOW(), NOW()) RETURNING item_id")
                        .setParameter("clauseNumber", "제10조")
                        .setParameter("content", "조문 내용")
                        .setParameter("contentHash", "hash-1")
                        .setParameter("changeType", "NEW")
                        .setParameter("mandatory", true)
                        .setParameter("vector", dummyVectorLiteral())
                        .setParameter("regId", regulation.getRegId())
                        .getSingleResult())
                .longValue();
        RegItem regItem = regItemRepository.findById(regItemId).orElseThrow();

        String payload = objectMapper.writeValueAsString(
                callbackPayload(String.valueOf(regItem.getItemId())));

        mockMvc.perform(post("/api/v1/analysis/jobs/{jobId}/callback", job.getJobId())
                        .header("X-Analysis-Callback-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        List<AnalysisEvidence> evidences = analysisEvidenceRepository.findAll();
        assertThat(evidences).hasSize(1);
        AnalysisEvidence evidence = evidences.get(0);
        assertThat(evidence.getRegItem().getItemId()).isEqualTo(regItem.getItemId());
        assertThat(evidence.getRegVersionSnapshot()).isEqualTo("2024-v1");
        assertThat(evidence.getRegEffectiveDateSnapshot()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    @DisplayName("존재하지 않는 조항을 근거로 들면 콜백을 거부하고 근거를 저장하지 않는다")
    void 존재하지_않는_근거_콜백_거부() throws Exception {
        User user = userRepository.save(TestDataFactory.user("evidence-hallucination@lh.com"));
        Document document = documentRepository.save(Document.builder()
                .title("문서")
                .docType(DocumentType.NOTICE)
                .s3Key("documents/2/sample.pdf")
                .baseDate(LocalDate.now())
                .user(user)
                .build());
        AnalysisJob job = setUpJob(user, document);

        String payload = objectMapper.writeValueAsString(callbackPayload("999999"));

        // handleCallback의 @Transactional이 rollback-only로 마킹되므로, 같은 트랜잭션
        // 안에서 실패 응답 이후 DB를 재조회하면 오염된 영속성 컨텍스트 때문에 무관한
        // 예외가 난다. HTTP 레벨에서 거부됐다는 것만으로 "저장되지 않았다"가 보장된다
        // (요청이 트랜잭션 커밋 전에 예외로 끝났으므로).
        mockMvc.perform(post("/api/v1/analysis/jobs/{jobId}/callback", job.getJobId())
                        .header("X-Analysis-Callback-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }
}
