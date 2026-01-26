package com.lh.assist.analysis.domain.repository;

import com.lh.assist.analysis.domain.entity.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
}