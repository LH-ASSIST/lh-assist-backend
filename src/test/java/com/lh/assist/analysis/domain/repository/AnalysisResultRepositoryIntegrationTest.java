package com.lh.assist.analysis.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.DocumentType;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserPosition;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import com.lh.assist.user.domain.repository.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AnalysisResultRepositoryIntegrationTest extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("문서별 최신 분석 결과만 집계되어야 한다")
    void 문서별_최신_분석_집계() {
        User user = userRepository.save(TestDataFactory.userWith(
                "repo@lh.com",
                "hashed",
                "Tester",
                UserRole.USER,
                UserDepartment.ETC,
                UserPosition.ETC,
                UserStatus.ACTIVE,
                true
        ));

        Document doc1 = documentRepository.save(Document.builder()
                .title("문서1")
                .docType(DocumentType.PLAN)
                .s3Key("documents/doc1.pdf")
                .baseDate(LocalDate.now())
                .user(user)
                .build());

        Document doc2 = documentRepository.save(Document.builder()
                .title("문서2")
                .docType(DocumentType.PLAN)
                .s3Key("documents/doc2.pdf")
                .baseDate(LocalDate.now())
                .user(user)
                .build());

        AnalysisResult oldResult = analysisResultRepository.save(AnalysisResult.builder()
                .document(doc1)
                .baseDate(doc1.getBaseDate())
                .totalRiskScore(10)
                .status(AnalysisResultStatus.SUCCEEDED)
                .build());

        AnalysisResult latestResult = analysisResultRepository.save(AnalysisResult.builder()
                .document(doc1)
                .baseDate(doc1.getBaseDate())
                .totalRiskScore(50)
                .status(AnalysisResultStatus.SUCCEEDED)
                .build());

        AnalysisResult doc2Result = analysisResultRepository.save(AnalysisResult.builder()
                .document(doc2)
                .baseDate(doc2.getBaseDate())
                .totalRiskScore(20)
                .status(AnalysisResultStatus.SUCCEEDED)
                .build());

        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        jdbcTemplate.update(
                "update analysis_results set created_at = ? where analysis_id = ?",
                Timestamp.valueOf(startOfMonth.plusDays(1)),
                oldResult.getAnalysisId()
        );
        jdbcTemplate.update(
                "update analysis_results set created_at = ? where analysis_id = ?",
                Timestamp.valueOf(startOfMonth.plusDays(2)),
                latestResult.getAnalysisId()
        );
        jdbcTemplate.update(
                "update analysis_results set created_at = ? where analysis_id = ?",
                Timestamp.valueOf(startOfMonth.plusDays(3)),
                doc2Result.getAnalysisId()
        );

        Double avg = analysisResultRepository.averageTotalRiskScoreLatestByDocumentBetween(
                user.getUserId(),
                AnalysisResultStatus.SUCCEEDED.name(),
                startOfMonth,
                startOfMonth.plusMonths(1)
        );

        long highRisk = analysisResultRepository.countHighRiskLatestByDocumentBetween(
                user.getUserId(),
                AnalysisResultStatus.SUCCEEDED.name(),
                40,
                startOfMonth,
                startOfMonth.plusMonths(1)
        );

        long lowRisk = analysisResultRepository.countLowRiskLatestByDocumentBetween(
                user.getUserId(),
                AnalysisResultStatus.SUCCEEDED.name(),
                20,
                startOfMonth,
                startOfMonth.plusMonths(1)
        );

        assertThat(avg).isCloseTo(35.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(highRisk).isEqualTo(1L);
        assertThat(lowRisk).isEqualTo(1L);
    }
}