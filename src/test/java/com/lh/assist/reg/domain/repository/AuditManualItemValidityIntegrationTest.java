package com.lh.assist.reg.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lh.assist.LhAssistBackendApplication;
import com.lh.assist.reg.domain.entity.AuditManual;
import com.lh.assist.reg.domain.entity.AuditManualItem;
import com.lh.assist.reg.domain.enums.ManualContentType;
import com.lh.assist.support.IntegrationTestBase;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사매뉴얼 시점 유효성 판정(findValidManualItemIdsAsOf)을 검증한다
 *
 * reg_items와 달리 audit_manuals는 지금까지 시점 필터링 대상이 아니었다. 감사매뉴얼은
 * 조문 단위로 부분 개정되는 법령과 달리 보통 문서 전체가 새 버전으로 교체되므로,
 * 조항이 아닌 매뉴얼(AuditManual) 단위로 발효/만료/활성 여부를 판단한다
 */
@Transactional
@SpringBootTest(classes = LhAssistBackendApplication.class)
class AuditManualItemValidityIntegrationTest extends IntegrationTestBase {

	@Autowired
	private AuditManualRepository auditManualRepository;

	@Autowired
	private AuditManualItemRepository auditManualItemRepository;

	private AuditManual saveManual(LocalDate effectiveDate, LocalDate expiryDate, boolean active) {
		return auditManualRepository.save(AuditManual.builder()
				.title("공공주택 감사매뉴얼")
				.code("MANUAL-" + System.nanoTime())
				.version("1.0")
				.effectiveDate(effectiveDate)
				.expiryDate(expiryDate)
				.active(active)
				.build());
	}

	private AuditManualItem saveItem(AuditManual manual, String articleName) {
		return auditManualItemRepository.save(AuditManualItem.builder()
				.auditManual(manual)
				.articleName(articleName)
				.sectionNumber(1)
				.contentType(ManualContentType.ARTICLE_DESC)
				.content("매뉴얼 내용 " + articleName)
				.build());
	}

	@Test
	@DisplayName("기준일이 매뉴얼 발효일 이후면 유효 목록에 포함된다")
	void 매뉴얼_발효일_이후_포함() {
		AuditManual manual = saveManual(
				LocalDate.of(2024, 1, 1), null, true);
		AuditManualItem item = saveItem(manual, "제1조");

		assertThat(auditManualItemRepository.findValidManualItemIdsAsOf(LocalDate.of(2024, 1, 1)))
				.contains(item.getManualItemId());
		assertThat(auditManualItemRepository.findValidManualItemIdsAsOf(LocalDate.of(2023, 12, 31)))
				.doesNotContain(item.getManualItemId());
	}

	@Test
	@DisplayName("구버전 매뉴얼이 만료되고 신버전으로 교체되면 기준일에는 신버전만 유효하다")
	void 매뉴얼_버전_교체() {
		AuditManual oldManual = saveManual(
				LocalDate.of(2020, 1, 1), LocalDate.of(2024, 6, 1), true);
		AuditManual newManual = saveManual(
				LocalDate.of(2024, 6, 1), null, true);
		AuditManualItem oldItem = saveItem(oldManual, "제1조");
		AuditManualItem newItem = saveItem(newManual, "제1조");

		var beforeSwitch = auditManualItemRepository.findValidManualItemIdsAsOf(LocalDate.of(2024, 1, 1));
		assertThat(beforeSwitch).contains(oldItem.getManualItemId());
		assertThat(beforeSwitch).doesNotContain(newItem.getManualItemId());

		var afterSwitch = auditManualItemRepository.findValidManualItemIdsAsOf(LocalDate.of(2024, 6, 1));
		assertThat(afterSwitch).contains(newItem.getManualItemId());
		assertThat(afterSwitch).doesNotContain(oldItem.getManualItemId());
	}

	@Test
	@DisplayName("매뉴얼이 수동 비활성화되면 발효 구간 안이어도 제외된다")
	void 매뉴얼_비활성화시_제외() {
		AuditManual manual = saveManual(
				LocalDate.of(2020, 1, 1), null, false);
		AuditManualItem item = saveItem(manual, "제1조");

		assertThat(auditManualItemRepository.findValidManualItemIdsAsOf(LocalDate.of(2024, 1, 1)))
				.doesNotContain(item.getManualItemId());
	}
}
