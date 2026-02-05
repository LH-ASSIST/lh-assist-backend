package com.lh.assist.analysis.domain.repository;

import com.lh.assist.analysis.domain.entity.AnalysisEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisEvidenceRepository extends JpaRepository<AnalysisEvidence, Long> {
    void deleteAllByAnalysisSection_AnalysisResult_AnalysisId(Long analysisId);
}