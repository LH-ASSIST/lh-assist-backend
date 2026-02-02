package com.lh.assist.analysis.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "analysis_sections")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "section_id")
    private Long sectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id", nullable = false)
    private AnalysisResult analysisResult;

    @Column(name = "external_section_id", nullable = false)
    private String externalSectionId;

    @Column(name = "bbox", columnDefinition = "TEXT")
    private String bbox;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "is_violation")
    private boolean isViolation;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnalysisSection that)) {
            return false;
        }
        return sectionId != null && sectionId.equals(that.sectionId);
    }

    @Override
    public int hashCode() {
        return sectionId != null ? sectionId.hashCode() : getClass().hashCode();
    }
}