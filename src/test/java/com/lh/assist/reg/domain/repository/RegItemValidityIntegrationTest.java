package com.lh.assist.reg.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lh.assist.LhAssistBackendApplication;
import com.lh.assist.reg.domain.entity.RegItem;
import com.lh.assist.reg.domain.entity.Regulation;
import com.lh.assist.reg.domain.enums.RegulationType;
import com.lh.assist.support.IntegrationTestBase;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조문 단위 유효성 판정(findValidItemIdsAsOf)의 경계값을 검증한다
 *
 * 발효일/만료일 경계, 폐지된 항목, 과도기 중복 적용까지 10가지 경계값 시나리오를
 * 다룬다. 각 케이스가 왜 크리티컬한지는 클래스 하단 주석 참고
 *
 * regulations 레벨 판정(1차 개선)은 "규정 전체가 통째로 개정된다"고 가정하는
 * 근사치였다. 실제로는 조문 하나만 개정되고 나머지는 그대로인 경우가 대부분이라,
 * 조문 자체에 effective_date/expiry_date를 지정할 수 있게 하고 지정하지 않으면
 * 소속 규정의 값을 물려받게 한 설계(2차 개선)를 여기서 검증한다
 */
@Transactional
@SpringBootTest(classes = LhAssistBackendApplication.class)
class RegItemValidityIntegrationTest extends IntegrationTestBase {

	@Autowired
	private RegulationRepository regulationRepository;

	@Autowired
	private RegItemRepository regItemRepository;

	@Autowired
	private EntityManager entityManager;

	private Regulation saveRegulation(LocalDate effectiveDate, LocalDate expiryDate) {
		return saveRegulation(effectiveDate, expiryDate, true);
	}

	private Regulation saveRegulation(LocalDate effectiveDate, LocalDate expiryDate, boolean active) {
		return regulationRepository.save(Regulation.builder()
				.title("공공주택 특별법")
				.regType(RegulationType.LAW)
				.effectiveDate(effectiveDate)
				.expiryDate(expiryDate)
				.active(active)
				.amendmentDate(effectiveDate)
				.version("1.0")
				.sourceUrl("https://law.go.kr/test")
				.build());
	}

	// JPA의 VectorStringConverter는 vector 컬럼에 varchar 바인드 타입을 그대로 넘겨
	// Postgres가 캐스팅을 거부한다. 실서비스에서도 reg_items 적재는 vector.py가
	// ::vector 캐스트를 명시한 raw SQL로 한다(JPA save()로 쓰지 않는다). 같은 방식으로
	// 네이티브 SQL에 명시적 캐스트를 줘서 실제 적재 경로를 재현한다.
	private RegItem saveItem(
			Regulation regulation,
			String clauseNumber,
			LocalDate effectiveDate,
			LocalDate expiryDate
	) {
		Long itemId = ((Number) entityManager.createNativeQuery(
						"INSERT INTO reg_items (clause_number, content, content_hash, change_type, "
								+ "is_mandatory, vector_index, reg_id, effective_date, expiry_date, "
								+ "created_at, updated_at) "
								+ "VALUES (:clauseNumber, :content, :contentHash, 'NEW', true, "
								+ "CAST(:vector AS vector), :regId, :effectiveDate, :expiryDate, "
								+ "NOW(), NOW()) RETURNING item_id")
						.setParameter("clauseNumber", clauseNumber)
						.setParameter("content", "조문 내용 " + clauseNumber)
						.setParameter("contentHash", clauseNumber + "-" + regulation.getRegId() + "-hash")
						.setParameter("vector", dummyVectorLiteral())
						.setParameter("regId", regulation.getRegId())
						.setParameter("effectiveDate", effectiveDate)
						.setParameter("expiryDate", expiryDate)
						.getSingleResult())
				.longValue();
		return regItemRepository.findById(itemId).orElseThrow();
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

	// --- 1~2. 발효일 경계 ---
	// 크리티컬한 이유: 시행 첫날 집행된 건을 감사할 때 그 날짜가 경계에 걸리면,
	// 하루 차이로 "아직 시행 전 규정"과 "막 시행된 규정"이 뒤바뀔 수 있다.

	@Test
	@DisplayName("1. 기준일이 발효일과 같으면 유효 목록에 포함된다")
	void 발효일_당일이면_포함() {
		Regulation regulation = saveRegulation(LocalDate.of(2024, 1, 1), null);
		RegItem item = saveItem(regulation, "제1조", null, null);

		assertThat(regItemRepository.findValidItemIdsAsOf(LocalDate.of(2024, 1, 1)))
				.contains(item.getItemId());
	}

	@Test
	@DisplayName("2. 기준일이 발효일 하루 전이면 유효 목록에서 제외된다")
	void 발효일_하루_전이면_제외() {
		Regulation regulation = saveRegulation(LocalDate.of(2024, 1, 1), null);
		RegItem item = saveItem(regulation, "제1조", null, null);

		assertThat(regItemRepository.findValidItemIdsAsOf(LocalDate.of(2023, 12, 31)))
				.doesNotContain(item.getItemId());
	}

	// --- 3~4. 만료일 경계 ---
	// 크리티컬한 이유: 만료일 당일까지 유효한지, 전날까지만 유효한지는 조문마다
	// 다를 수 있어(예: "제3항 시행 후 6개월 경과일부터" 식 부칙) 경계 처리를 반대로
	// 하면 이미 폐지된 조항을 근거로 감사 판정을 내리게 된다.

	@Test
	@DisplayName("3. 기준일이 만료일과 같으면 유효 목록에서 제외된다")
	void 만료일_당일이면_제외() {
		Regulation regulation = saveRegulation(LocalDate.of(2020, 1, 1), LocalDate.of(2024, 6, 1));
		RegItem item = saveItem(regulation, "제1조", null, null);

		assertThat(regItemRepository.findValidItemIdsAsOf(LocalDate.of(2024, 6, 1)))
				.doesNotContain(item.getItemId());
	}

	@Test
	@DisplayName("4. 기준일이 만료일 하루 전이면 유효 목록에 포함된다")
	void 만료일_하루_전이면_포함() {
		Regulation regulation = saveRegulation(LocalDate.of(2020, 1, 1), LocalDate.of(2024, 6, 1));
		RegItem item = saveItem(regulation, "제1조", null, null);

		assertThat(regItemRepository.findValidItemIdsAsOf(LocalDate.of(2024, 5, 31)))
				.contains(item.getItemId());
	}

	// --- 5. 무기한 규정 ---
	// 크리티컬한 이유: expiry_date가 NULL인 경우를 "만료됨"으로 잘못 처리하면
	// 현재도 유효한 대다수의 규정이 전부 검색에서 빠지는 치명적 회귀가 난다.

	@Test
	@DisplayName("5. 만료일이 없는 무기한 규정은 먼 미래 기준일에도 유효하다")
	void 만료일_없는_무기한_규정은_먼_미래에도_유효() {
		Regulation regulation = saveRegulation(LocalDate.of(2020, 1, 1), null);
		RegItem item = saveItem(regulation, "제1조", null, null);

		assertThat(regItemRepository.findValidItemIdsAsOf(LocalDate.of(2099, 1, 1)))
				.contains(item.getItemId());
	}

	// --- 6~8. 폐지된 항목 / 과도기 중복 적용 (조문 단위 개정 실사례) ---
	// 크리티컬한 이유: 실제 법령 개정은 조문 하나만 바뀌고 나머지는 그대로인 경우가
	// 대부분이다. 구법 조문과 신법 조문을 잘못 고르면(둘 다 같은 clause_number를
	// 참조) 감사 판정이 완전히 틀린 근거로 나간다. 이게 이 프로젝트에서 문서 단위
	// 설계를 조문 단위로 다시 설계하게 된 핵심 계기였다.

	@Test
	@DisplayName("6. 구법 조문 만료일과 신법 조문 발효일이 같은 날이면 기준일에는 신법만 유효하다")
	void 구법_만료일과_신법_발효일이_같은_날이면_신법만_유효() {
		Regulation regulation = saveRegulation(LocalDate.of(2020, 1, 1), null);
		RegItem oldClause = saveItem(regulation, "제10조(구)", LocalDate.of(2020, 1, 1), LocalDate.of(2024, 6, 1));
		RegItem newClause = saveItem(regulation, "제10조(신)", LocalDate.of(2024, 6, 1), null);

		List<Long> validAtSwitch = regItemRepository.findValidItemIdsAsOf(LocalDate.of(2024, 6, 1));
		assertThat(validAtSwitch).contains(newClause.getItemId());
		assertThat(validAtSwitch).doesNotContain(oldClause.getItemId());
	}

	@Test
	@DisplayName("7. 과도기 중첩 구간에서는 구법과 신법이 동시에 유효할 수 있다")
	void 과도기_중첩_구간에서는_구법과_신법이_동시에_유효할_수_있다() {
		Regulation regulation = saveRegulation(LocalDate.of(2020, 1, 1), null);
		// 부칙에 유예기간이 있어 신법 시행 이후에도 일정 기간 구법이 함께 적용되는 경우
		RegItem oldClause = saveItem(regulation, "제10조(구)", LocalDate.of(2020, 1, 1), LocalDate.of(2024, 9, 1));
		RegItem newClause = saveItem(regulation, "제10조(신)", LocalDate.of(2024, 6, 1), null);

		List<Long> duringOverlap = regItemRepository.findValidItemIdsAsOf(LocalDate.of(2024, 7, 1));
		assertThat(duringOverlap).contains(oldClause.getItemId(), newClause.getItemId());
	}

	@Test
	@DisplayName("8. 제10조만 개정되면 나머지 조문은 그대로 유효하고 제10조만 신구 버전으로 나뉜다")
	void 조문_단위_개정_나머지_조문_영향없음() {
		// 규정 자체는 2020년에 발효된 채로 유지되고, 제10조 하나만 2024-06-01에 개정됐다고 가정.
		// 문서 단위 판정이었다면 규정 전체를 복제해야 했을 시나리오를 조문 하나만으로 표현한다.
		Regulation regulation = saveRegulation(LocalDate.of(2020, 1, 1), null);

		RegItem unaffected = saveItem(regulation, "제9조", null, null);
		RegItem oldClause10 = saveItem(
				regulation, "제10조(구)", LocalDate.of(2020, 1, 1), LocalDate.of(2024, 6, 1));
		RegItem newClause10 = saveItem(
				regulation, "제10조(신)", LocalDate.of(2024, 6, 1), null);

		List<Long> beforeAmendment = regItemRepository.findValidItemIdsAsOf(LocalDate.of(2023, 1, 1));
		assertThat(beforeAmendment).contains(unaffected.getItemId(), oldClause10.getItemId());
		assertThat(beforeAmendment).doesNotContain(newClause10.getItemId());

		List<Long> afterAmendment = regItemRepository.findValidItemIdsAsOf(LocalDate.of(2024, 6, 1));
		assertThat(afterAmendment).contains(unaffected.getItemId(), newClause10.getItemId());
		assertThat(afterAmendment).doesNotContain(oldClause10.getItemId());
	}

	// --- 9. 미래 시행 예정 규정 ---
	// 크리티컬한 이유: 미리 적재해둔 개정안이 시행일 전에 검색되면, 아직 발효되지
	// 않은 규정을 근거로 판정하는 것과 같은 오류가 난다.

	@Test
	@DisplayName("9. 아직 시행되지 않은 미래 규정은 기준일 기준으로 제외된다")
	void 아직_시행되지_않은_미래_규정은_제외() {
		Regulation regulation = saveRegulation(LocalDate.of(2099, 1, 1), null);
		RegItem item = saveItem(regulation, "제1조", null, null);

		assertThat(regItemRepository.findValidItemIdsAsOf(LocalDate.of(2024, 1, 1)))
				.doesNotContain(item.getItemId());
	}

	// --- 10. 조문 날짜 상속 / 수동 비활성화 ---
	// 크리티컬한 이유: 조문 대부분은 규정과 같은 시점에 발효되므로, 상속 로직이
	// 깨지면 조문 전체가 검색에서 빠지는 광범위한 회귀가 난다. 수동 비활성화는
	// 날짜 계산으로 잡을 수 없는 관리자 판단(오류 규정 긴급 차단 등)의 최종 방어선이다.

	@Test
	@DisplayName("10a. 조문에 별도 날짜가 없으면 소속 규정의 발효/만료일을 그대로 물려받는다")
	void 조문_날짜_미지정시_규정_날짜_상속() {
		Regulation regulation = saveRegulation(LocalDate.of(2024, 1, 1), null);
		RegItem item = saveItem(regulation, "제1조", null, null);

		assertThat(regItemRepository.findValidItemIdsAsOf(LocalDate.of(2024, 1, 1)))
				.contains(item.getItemId());
		assertThat(regItemRepository.findValidItemIdsAsOf(LocalDate.of(2023, 12, 31)))
				.doesNotContain(item.getItemId());
	}

	@Test
	@DisplayName("10b. 규정이 수동 비활성화되면 조문에 개별 유효기간이 있어도 제외된다")
	void 규정_비활성화시_조문도_제외() {
		Regulation regulation = saveRegulation(LocalDate.of(2020, 1, 1), null, false);
		RegItem item = saveItem(regulation, "제1조", LocalDate.of(2020, 1, 1), null);

		assertThat(regItemRepository.findValidItemIdsAsOf(LocalDate.of(2024, 1, 1)))
				.doesNotContain(item.getItemId());
	}
}
