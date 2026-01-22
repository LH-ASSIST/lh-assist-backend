package com.lh.assist.analysis.domain.repository;

import com.lh.assist.analysis.domain.entity.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {
}