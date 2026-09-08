package com.lh.assist.reg.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lh.assist.LhAssistBackendApplication;
import com.lh.assist.reg.domain.entity.RegItem;
import com.lh.assist.reg.domain.entity.Regulation;
import com.lh.assist.reg.domain.enums.RegulationType;
import com.lh.assist.support.IntegrationTestBase;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기준일 필터링 도입 전/후의 "시점 오염률(temporal contamination rate)"을 측정한다
 *
 * LLM 최종 판정 정확도(RAGAS 등)는 API 비용이 들고 모델 비결정성 때문에 이
 * 저장소만으로 재현 가능한 지표가 아니다. 대신 이 개선이 실제로 바꾸는 것 —
 * "검색 후보 중 시점상 무효한 규정의 비율" — 은 SQL만으로 결정적으로 측정 가능하고,
 * 이게 정확히 이 개선이 통제하는 변수다. AI 판정이 틀렸던 근본 원인은 검색
 * 후보 자체에 시점상 무효한 규정이 섞여 있었다는 것이므로, 오염률을 0으로
 * 만들면 그 원인이 제거됐다는 걸 직접 증명한다
 *
 * 개선 전 동작(WHERE r.is_active = true 만 봄)을 baseline으로 재현하고,
 * 개선 후 findValidItemIdsAsOf 결과와 비교한다
 */
@Transactional
@SpringBootTest(classes = LhAssistBackendApplication.class)
class RegItemTemporalContaminationBenchmarkTest extends IntegrationTestBase {

	@Autowired
	private RegulationRepository regulationRepository;

	@Autowired
	private RegItemRepository regItemRepository;

	@Autowired
	private EntityManager entityManager;

	private Regulation saveRegulation(LocalDate effectiveDate, LocalDate expiryDate) {
		return regulationRepository.save(Regulation.builder()
				.title("공공주택 특별법")
				.regType(RegulationType.LAW)
				.effectiveDate(effectiveDate)
				.expiryDate(expiryDate)
				.active(true)
				.amendmentDate(effectiveDate)
				.version("1.0")
				.sourceUrl("https://law.go.kr/test")
				.build());
	}

	private RegItem saveItem(Regulation regulation, String clauseNumber, LocalDate effectiveDate, LocalDate expiryDate) {
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

	/**
	 * 개선 전 동작을 재현: is_active만 보고 날짜는 전혀 안 본다
	 * (SqsMessageProducer.sendAnalysisRequested가 baseDate/validItemIds를
	 * 넘기기 전 retrieval.py의 실제 쿼리 조건이었다)
	 */
	@SuppressWarnings("unchecked")
	private Set<Long> legacyCandidateItemIds() {
		List<Number> rows = entityManager.createNativeQuery(
						"SELECT ri.item_id FROM reg_items ri "
								+ "JOIN regulations r ON r.reg_id = ri.reg_id "
								+ "WHERE r.is_active = true")
				.getResultList();
		Set<Long> ids = new HashSet<>();
		rows.forEach(n -> ids.add(n.longValue()));
		return ids;
	}

	@Test
	@DisplayName("실측 코퍼스에서 시점 필터링 전/후 오염률을 측정한다")
	void 시점_필터링_전후_오염률_측정() {
		LocalDate baseDate = LocalDate.of(2023, 1, 1);

		// 규정 A: 2015년 발효, 상시 유효 조문 5개 + 2024-06-01에 개정된 제10조(구/신)
		Regulation regA = saveRegulation(LocalDate.of(2015, 1, 1), null);
		List<RegItem> evergreenA = List.of(
				saveItem(regA, "제1조", null, null),
				saveItem(regA, "제2조", null, null),
				saveItem(regA, "제3조", null, null),
				saveItem(regA, "제4조", null, null),
				saveItem(regA, "제5조", null, null)
		);
		RegItem oldClause10 = saveItem(regA, "제10조(구)", LocalDate.of(2015, 1, 1), LocalDate.of(2024, 6, 1));
		RegItem newClause10 = saveItem(regA, "제10조(신)", LocalDate.of(2024, 6, 1), null);

		// 규정 B: 2022년 발효, 상시 유효 조문 3개
		Regulation regB = saveRegulation(LocalDate.of(2022, 1, 1), null);
		List<RegItem> evergreenB = List.of(
				saveItem(regB, "제1조", null, null),
				saveItem(regB, "제2조", null, null),
				saveItem(regB, "제3조", null, null)
		);

		// 규정 C: 2099년 시행 예정(아직 시행 전인데 미리 적재된 개정안), is_active=true
		Regulation regC = saveRegulation(LocalDate.of(2099, 1, 1), null);
		List<RegItem> futureC = List.of(
				saveItem(regC, "제1조", null, null),
				saveItem(regC, "제2조", null, null)
		);

		// 규정 D: 2010년 발효, 2020년 만료(이미 폐지) - is_active 플래그 갱신이
		// 누락돼 여전히 true인 흔한 운영 실수를 재현
		Regulation regD = saveRegulation(LocalDate.of(2010, 1, 1), LocalDate.of(2020, 1, 1));
		List<RegItem> expiredD = List.of(
				saveItem(regD, "제1조", null, null),
				saveItem(regD, "제2조", null, null)
		);

		Set<Long> legacyCandidates = legacyCandidateItemIds();
		List<Long> filtered = regItemRepository.findValidItemIdsAsOf(baseDate);

		Set<Long> temporallyInvalidInLegacy = new HashSet<>(legacyCandidates);
		temporallyInvalidInLegacy.removeAll(filtered);

		double legacyContaminationRate = (double) temporallyInvalidInLegacy.size() / legacyCandidates.size();
		double filteredContaminationRate = filtered.stream()
				.filter(id -> !legacyCandidates.contains(id))
				.count() / (double) filtered.size();

		System.out.printf(
				"[오염률 측정] 개선 전 후보 %d개 중 시점상 무효 %d개 (오염률 %.1f%%) -> 개선 후 후보 %d개, 오염률 %.1f%%%n",
				legacyCandidates.size(), temporallyInvalidInLegacy.size(), legacyContaminationRate * 100,
				filtered.size(), filteredContaminationRate * 100
		);

		// 개선 전: 14개 후보(evergreenA 5 + 구법/신법 2 + evergreenB 3 + futureC 2 + expiredD 2)
		assertThat(legacyCandidates).hasSize(14);
		// 신법 제10조(아직 미시행) + futureC(2) + expiredD(2) = 5개가 시점상 무효한데도 섞여 있었다
		assertThat(temporallyInvalidInLegacy).hasSize(5);
		assertThat(temporallyInvalidInLegacy).contains(
				newClause10.getItemId(), futureC.get(0).getItemId(), futureC.get(1).getItemId(),
				expiredD.get(0).getItemId(), expiredD.get(1).getItemId());
		assertThat(legacyContaminationRate).isCloseTo(5.0 / 14.0, org.assertj.core.data.Offset.offset(0.001));

		// 개선 후: evergreenA(5) + 구법(1) + evergreenB(3) = 9개, 오염 0
		assertThat(filtered).hasSize(9);
		assertThat(filteredContaminationRate).isZero();
		assertThat(filtered).containsAll(evergreenA.stream().map(RegItem::getItemId).toList());
		assertThat(filtered).contains(oldClause10.getItemId());
		assertThat(filtered).containsAll(evergreenB.stream().map(RegItem::getItemId).toList());
		assertThat(filtered).doesNotContain(newClause10.getItemId());
	}
}
