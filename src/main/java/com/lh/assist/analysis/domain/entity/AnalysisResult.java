package com.lh.assist.analysis.domain.entity;

import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.regulation.domain.enums.domain.entity.Document;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "analysis_results")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisResult extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long analysisId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "doc_id", nullable = false)
	private Document document;

	@Column(name = "base_date", nullable = false)
	private LocalDate baseDate;

	@Column(name = "total_risk_score")
	private Integer totalRiskScore;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AnalysisResultStatus status;

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AnalysisResult that)) {
			return false;
		}
		return analysisId != null && analysisId.equals(that.analysisId);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}