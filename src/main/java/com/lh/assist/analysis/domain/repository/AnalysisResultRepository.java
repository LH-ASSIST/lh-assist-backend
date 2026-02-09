package com.lh.assist.analysis.domain.repository;

import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {
	Optional<AnalysisResult> findTopByDocument_DocIdAndStatusOrderByCreatedAtDesc(
			Long docId,
			AnalysisResultStatus status
	);

	Optional<AnalysisResult> findTopByDocument_DocIdOrderByCreatedAtDesc(Long docId);

	@Query(value = """
			with latest as (
				select distinct on (r.doc_id)
					r.doc_id,
					r.total_risk_score
				from analysis_results r
				join documents d on d.doc_id = r.doc_id
				where d.user_id = :userId
				and r.status = :status
				and r.total_risk_score is not null
				and r.created_at >= :start
				and r.created_at < :end
				order by r.doc_id, r.created_at desc
			)
			select avg(total_risk_score) from latest
			""", nativeQuery = true)
	Double averageTotalRiskScoreLatestByDocumentBetween(
			@Param("userId") Long userId,
			@Param("status") String status,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end
	);

	@Query(value = """
			with latest as (
				select distinct on (r.doc_id)
					r.doc_id,
					r.total_risk_score
				from analysis_results r
				join documents d on d.doc_id = r.doc_id
				where d.user_id = :userId
				and r.status = :status
				and r.total_risk_score is not null
				and r.created_at >= :start
				and r.created_at < :end
				order by r.doc_id, r.created_at desc
			)
			select count(*)
			from latest
			where total_risk_score >= :riskScoreThreshold
			""", nativeQuery = true)
	long countHighRiskLatestByDocumentBetween(
			@Param("userId") Long userId,
			@Param("status") String status,
			@Param("riskScoreThreshold") int riskScoreThreshold,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end
	);

	@Query(value = """
			with latest as (
				select distinct on (r.doc_id)
					r.doc_id,
					r.total_risk_score
				from analysis_results r
				join documents d on d.doc_id = r.doc_id
				where d.user_id = :userId
				and r.status = :status
				and r.total_risk_score is not null
				and r.created_at >= :start
				and r.created_at < :end
				order by r.doc_id, r.created_at desc
			)
			select count(*)
			from latest
			where total_risk_score <= :riskScoreThreshold
			""", nativeQuery = true)
	long countLowRiskLatestByDocumentBetween(
			@Param("userId") Long userId,
			@Param("status") String status,
			@Param("riskScoreThreshold") int riskScoreThreshold,
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end
	);
}