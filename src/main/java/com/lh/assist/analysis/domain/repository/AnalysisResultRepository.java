package com.lh.assist.analysis.domain.repository;

import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {
	Optional<AnalysisResult> findTopByDocument_DocIdAndStatusOrderByCreatedAtDesc(
			Long docId,
			AnalysisResultStatus status
	);
}