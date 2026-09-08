package com.lh.assist.reg.domain.repository;

import com.lh.assist.reg.domain.entity.AuditManualItem;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditManualItemRepository extends JpaRepository<AuditManualItem, Long> {

	/**
	 * 기준일에 유효한 감사매뉴얼 조항의 ID 목록을 조회한다
	 *
	 * 감사매뉴얼은 조문 단위로 부분 개정되는 법령과 달리 보통 문서 전체가 새
	 * 버전으로 교체되므로, 소속 매뉴얼(audit_manuals)의 발효일/만료일/활성 여부로
	 * 판단한다
	 *
	 * @param baseDate 기준 일자
	 * @return 기준일에 유효한 감사매뉴얼 조항 ID 목록
	 */
	@Query("SELECT ami.manualItemId FROM AuditManualItem ami "
			+ "WHERE ami.auditManual.active = true "
			+ "AND ami.auditManual.effectiveDate <= :baseDate "
			+ "AND (ami.auditManual.expiryDate IS NULL OR ami.auditManual.expiryDate > :baseDate)")
	List<Long> findValidManualItemIdsAsOf(@Param("baseDate") LocalDate baseDate);
}
