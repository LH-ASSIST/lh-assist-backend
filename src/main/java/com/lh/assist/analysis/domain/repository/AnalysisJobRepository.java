package com.lh.assist.analysis.domain.repository;

import com.lh.assist.analysis.domain.entity.AnalysisJob;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
	long countByRequestedBy_UserIdAndCreatedAtBetween(
			Long userId,
			LocalDateTime start,
			LocalDateTime end
	);

	@Query("""
			select distinct j.requestedBy.userId
			from AnalysisJob j
			where j.createdAt >= :start
			and j.createdAt < :end
			""")
	List<Long> findDistinctUserIdsByCreatedAtBetween(
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end
	);
}