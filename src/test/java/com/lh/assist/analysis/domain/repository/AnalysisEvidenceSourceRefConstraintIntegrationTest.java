package com.lh.assist.analysis.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lh.assist.LhAssistBackendApplication;
import com.lh.assist.analysis.domain.entity.AnalysisEvidence;
import com.lh.assist.analysis.domain.entity.AnalysisJob;
import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.entity.AnalysisSection;
import com.lh.assist.analysis.domain.enums.AnalysisEvidenceSourceType;
import com.lh.assist.analysis.domain.enums.AnalysisJobStatus;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.document.domain.entity.Document;
import com.lh.assist.document.domain.enums.DocumentType;
import com.lh.assist.document.domain.repository.DocumentRepository;
import com.lh.assist.reg.domain.entity.AuditItem;
import com.lh.assist.reg.domain.repository.AuditItemRepository;
import com.lh.assist.support.IntegrationTestBase;
import com.lh.assist.support.TestDataFactory;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * analysis_evidences의 다형 참조 배타성 CHECK 제약(chk_analysis_evidences_source_ref)을 검증한다
 *
 * source_type이 가리키는 FK 컬럼 하나만 채워지고 나머지 둘은 NULL이어야 한다.
 * AnalysisCallbackService의 Java 레벨 검증(존재하지 않는 조항 거부)과는 별개로,
 * DB 제약 자체가 실제로 걸려 있는지를 리포지토리 레벨에서 직접 확인한다
 */
@Transactional
@SpringBootTest(classes = LhAssistBackendApplication.class)
class AnalysisEvidenceSourceRefConstraintIntegrationTest extends IntegrationTestBase {

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
	private AuditItemRepository auditItemRepository;

	private AnalysisSection setUpSection() {
		User user = userRepository.save(TestDataFactory.user("evidence-constraint@lh.com"));
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
		analysisJobRepository.save(AnalysisJob.builder()
				.analysisResult(result)
				.document(document)
				.baseDate(document.getBaseDate())
				.status(AnalysisJobStatus.REQUESTED)
				.retryCount(0)
				.requestedBy(user)
				.build());
		return analysisSectionRepository.save(AnalysisSection.builder()
				.analysisResult(result)
				.externalSectionId("sec-1")
				.pageNumber(1)
				.isViolation(true)
				.riskScore(50)
				.reasoning("테스트")
				.build());
	}

	@Test
	@DisplayName("source_type과 일치하는 FK 하나만 채우면 정상 저장된다")
	void 배타적_FK_하나만_채우면_정상_저장() {
		AnalysisSection section = setUpSection();
		AuditItem auditItem = auditItemRepository.save(AuditItem.builder()
				.docId("case-doc-1")
				.chunkIndex(0)
				.content("사례 내용")
				.build());

		AnalysisEvidence evidence = analysisEvidenceRepository.save(AnalysisEvidence.builder()
				.analysisSection(section)
				.sourceType(AnalysisEvidenceSourceType.AUDIT_ITEM)
				.sourceId(String.valueOf(auditItem.getItemId()))
				.auditItem(auditItem)
				.quote("사례 원문")
				.build());

		assertThat(evidence.getEvidenceId()).isNotNull();
	}

	@Test
	@DisplayName("source_type이 REG_ITEM인데 어떤 FK도 채우지 않으면 저장이 거부된다")
	void 배타적_FK_미충족시_저장_거부() {
		AnalysisSection section = setUpSection();

		assertThatThrownBy(() -> analysisEvidenceRepository.save(AnalysisEvidence.builder()
				.analysisSection(section)
				.sourceType(AnalysisEvidenceSourceType.REG_ITEM)
				.sourceId("999")
				.quote("근거 없이 그냥 인용")
				.build()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("chk_analysis_evidences_source_ref");
	}
}
