package com.lh.assist.analysis.domain.repository;

import com.lh.assist.analysis.domain.entity.AnalysisSection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisSectionRepository extends JpaRepository<AnalysisSection, Long> {
    List<AnalysisSection> findAllByAnalysisResult_AnalysisId(Long analysisId);
    long countByAnalysisResult_AnalysisIdAndIsViolationTrue(Long analysisId);
}