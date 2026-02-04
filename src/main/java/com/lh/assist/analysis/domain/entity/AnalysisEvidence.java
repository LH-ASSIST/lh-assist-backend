package com.lh.assist.analysis.domain.entity;

import com.lh.assist.analysis.domain.enums.AnalysisEvidenceSourceType;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "analysis_evidences")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evidence_id")
    private Long evidenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private AnalysisSection analysisSection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "risk_id")
    private AnalysisRiskItem analysisRiskItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private AnalysisEvidenceSourceType sourceType;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "quote", columnDefinition = "TEXT")
    private String quote;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnalysisEvidence that)) {
            return false;
        }
        return evidenceId != null && evidenceId.equals(that.evidenceId);
    }

    @Override
    public int hashCode() {
        return evidenceId != null ? evidenceId.hashCode() : getClass().hashCode();
    }
}