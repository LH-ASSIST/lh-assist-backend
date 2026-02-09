package com.lh.assist.analysis.domain.entity;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.user.domain.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "analysis_dashboard_summaries",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_analysis_dashboard_summaries_user_month",
                columnNames = {"user_id", "year_month"}
        )
)
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisDashboardSummary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "summary_id")
    private Long summaryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "monthly_review_count", nullable = false)
    private long monthlyReviewCount;

    @Column(name = "high_risk_document_count", nullable = false)
    private long highRiskDocumentCount;

    @Column(name = "average_safety_score", nullable = false)
    private int averageSafetyScore;

    @Column(name = "low_risk_document_count", nullable = false)
    private long lowRiskDocumentCount;

    public void updateStats(
            long monthlyReviewCount,
            long highRiskDocumentCount,
            int averageSafetyScore,
            long lowRiskDocumentCount
    ) {
        this.monthlyReviewCount = monthlyReviewCount;
        this.highRiskDocumentCount = highRiskDocumentCount;
        this.averageSafetyScore = averageSafetyScore;
        this.lowRiskDocumentCount = lowRiskDocumentCount;
    }
}