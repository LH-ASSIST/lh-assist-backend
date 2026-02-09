package com.lh.assist.analysis.domain.repository;

import com.lh.assist.analysis.domain.entity.AnalysisDashboardSummary;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisDashboardSummaryRepository extends JpaRepository<AnalysisDashboardSummary, Long> {
    Optional<AnalysisDashboardSummary> findByUser_UserIdAndYearMonth(Long userId, String yearMonth);
}