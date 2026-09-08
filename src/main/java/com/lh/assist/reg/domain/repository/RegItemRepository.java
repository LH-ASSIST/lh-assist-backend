package com.lh.assist.reg.domain.repository;

import com.lh.assist.reg.domain.entity.RegItem;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegItemRepository extends JpaRepository<RegItem, Long> {

	/**
	 * 기준일에 유효한 조문의 ID 목록을 조회한다(조문 단위 정밀 판정)
	 *
	 * 조문 자체에 effective_date/expiry_date가 지정돼 있으면 그 값을, 없으면 소속
	 * 규정의 값을 물려받아 판단한다. 규정이 수동으로 비활성화된 경우는 제외한다
	 *
	 * @param baseDate 기준 일자
	 * @return 기준일에 유효한 조문 ID 목록
	 */
	@Query("SELECT ri.itemId FROM RegItem ri "
			+ "WHERE ri.regulation.active = true "
			+ "AND COALESCE(ri.effectiveDate, ri.regulation.effectiveDate) <= :baseDate "
			+ "AND (COALESCE(ri.expiryDate, ri.regulation.expiryDate) IS NULL "
			+ "     OR COALESCE(ri.expiryDate, ri.regulation.expiryDate) > :baseDate)")
	List<Long> findValidItemIdsAsOf(@Param("baseDate") LocalDate baseDate);
}

