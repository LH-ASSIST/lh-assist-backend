package com.lh.assist.analysis.domain.entity;

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
import com.lh.assist.analysis.domain.enums.AnalysisRiskType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "analysis_risk_items")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisRiskItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "risk_id")
    private Long riskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private AnalysisSection analysisSection;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_type", nullable = false)
    private AnalysisRiskType riskType;

    @Column(name = "detected_text", columnDefinition = "TEXT", nullable = false)
    private String detectedText;

    @Column(name = "guide_message", columnDefinition = "TEXT", nullable = false)
    private String guideMessage;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "similar_case_content", columnDefinition = "TEXT")
    private String similarCaseContent;

    @Column(name = "reasoning", columnDefinition = "TEXT", nullable = false)
    private String reasoning;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnalysisRiskItem that)) {
            return false;
        }
        return riskId != null && riskId.equals(that.riskId);
    }

    @Override
    public int hashCode() {
        return riskId != null ? riskId.hashCode() : getClass().hashCode();
    }
}