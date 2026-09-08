package com.lh.assist.analysis.domain.entity;

import com.lh.assist.analysis.domain.enums.AnalysisEvidenceSourceType;
import com.lh.assist.reg.domain.entity.AuditItem;
import com.lh.assist.reg.domain.entity.AuditManualItem;
import com.lh.assist.reg.domain.entity.RegItem;
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
import org.hibernate.annotations.Check;

@Getter
@Entity
@Table(name = "analysis_evidences")
@Check(name = "chk_analysis_evidences_source_ref", constraints = """
        (source_type = 'REG_ITEM'
            AND reg_item_id IS NOT NULL
            AND audit_manual_item_id IS NULL
            AND audit_item_id IS NULL)
        OR (source_type = 'AUDIT_MANUAL_ITEM'
            AND audit_manual_item_id IS NOT NULL
            AND reg_item_id IS NULL
            AND audit_item_id IS NULL)
        OR (source_type = 'AUDIT_ITEM'
            AND audit_item_id IS NOT NULL
            AND reg_item_id IS NULL
            AND audit_manual_item_id IS NULL)
        """)
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reg_item_id")
    private RegItem regItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_manual_item_id")
    private AuditManualItem auditManualItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_item_id")
    private AuditItem auditItem;

    /**
     * REG_ITEM 근거의 판정 시점 규정 버전 스냅샷
     *
     * 이후 규정이 개정되어도 당시 판정에 실제로 쓰인 버전을 그대로 재현할 수 있게 한다
     */
    @Column(name = "reg_version_snapshot", length = 100)
    private String regVersionSnapshot;

    @Column(name = "reg_effective_date_snapshot")
    private LocalDate regEffectiveDateSnapshot;

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