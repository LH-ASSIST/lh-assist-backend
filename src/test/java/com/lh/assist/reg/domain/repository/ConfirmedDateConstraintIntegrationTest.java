package com.lh.assist.reg.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lh.assist.LhAssistBackendApplication;
import com.lh.assist.reg.domain.entity.AuditManual;
import com.lh.assist.reg.domain.entity.Regulation;
import com.lh.assist.reg.domain.enums.RegulationType;
import com.lh.assist.support.IntegrationTestBase;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * confirmed_date &lt;= effective_date CHECK 제약을 검증한다
 *
 * 확정일이 NULL이거나 시행일 이전이면 정상 저장되고, 시행일보다 나중이면
 * "확정되기도 전에 시행되는" 모순이므로 DB가 거부해야 한다
 */
@Transactional
@SpringBootTest(classes = LhAssistBackendApplication.class)
class ConfirmedDateConstraintIntegrationTest extends IntegrationTestBase {

	@Autowired
	private RegulationRepository regulationRepository;

	@Autowired
	private AuditManualRepository auditManualRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("확정일이 NULL이면 정상 저장된다")
	void 확정일_NULL이면_정상_저장() {
		Regulation regulation = regulationRepository.save(Regulation.builder()
				.title("공공주택 특별법")
				.regType(RegulationType.LAW)
				.effectiveDate(LocalDate.of(2024, 1, 1))
				.active(true)
				.amendmentDate(LocalDate.of(2024, 1, 1))
				.version("1.0")
				.sourceUrl("https://law.go.kr/test")
				.build());

		assertThat(regulation.getConfirmedDate()).isNull();
	}

	@Test
	@DisplayName("확정일이 시행일보다 이전이면 정상 저장된다")
	void 확정일_시행일_이전이면_정상_저장() {
		AuditManual manual = auditManualRepository.save(AuditManual.builder()
				.title("공공주택 감사매뉴얼")
				.code("MANUAL-" + System.nanoTime())
				.version("1.0")
				.confirmedDate(LocalDate.of(2025, 8, 29))
				.effectiveDate(LocalDate.of(2025, 9, 8))
				.active(true)
				.build());

		assertThat(manual.getConfirmedDate()).isEqualTo(LocalDate.of(2025, 8, 29));
	}

	@Test
	@DisplayName("확정일이 시행일보다 나중이면 저장이 거부된다")
	void 확정일_시행일_이후이면_저장_거부() {
		assertThatThrownBy(() -> {
			auditManualRepository.save(AuditManual.builder()
					.title("모순된 매뉴얼")
					.code("MANUAL-BAD-" + System.nanoTime())
					.version("1.0")
					.confirmedDate(LocalDate.of(2025, 9, 20))
					.effectiveDate(LocalDate.of(2025, 9, 8))
					.active(true)
					.build());
			entityManager.flush();
		})
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("chk_audit_manuals_confirmed_before_effective");
	}
}
