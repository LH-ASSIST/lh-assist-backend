package com.lh.assist.analysis.domain.repository;

import com.lh.assist.analysis.domain.entity.AnalysisRiskItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRiskItemRepository extends JpaRepository<AnalysisRiskItem, Long> {
    List<AnalysisRiskItem> findAllByAnalysisSection_SectionIdIn(List<Long> sectionIds);
    void deleteAllByAnalysisSection_AnalysisResult_AnalysisId(Long analysisId);
}