package com.lh.assist.analysis.domain;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.document.domain.Document;
import com.lh.assist.user.domain.User;
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
@Table(name = "analysis_jobs")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisJob extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long jobId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "analysis_id", nullable = false)
	private AnalysisResult analysisResult;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "doc_id", nullable = false)
	private Document document;

	@Column(name = "base_date", nullable = false)
	private LocalDate baseDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AnalysisJobStatus status;

	@Column(name = "fail_reason", columnDefinition = "TEXT")
	private String failReason;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "requested_by", nullable = false)
	private User requestedBy;

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AnalysisJob that)) {
			return false;
		}
		return jobId != null && jobId.equals(that.jobId);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}