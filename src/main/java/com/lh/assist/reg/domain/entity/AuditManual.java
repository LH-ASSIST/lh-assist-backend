package com.lh.assist.reg.domain.entity;

import com.lh.assist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Check;

@Getter
@Entity
@Table(name = "audit_manuals")
@Check(name = "chk_audit_manuals_confirmed_before_effective",
		constraints = "confirmed_date IS NULL OR confirmed_date <= effective_date")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditManual extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "manual_id")
    private Long manualId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "code", length = 50, unique = true)
    private String code;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /**
     * 확정일(결재 확정일)
     *
     * 기록용 메타데이터일 뿐 유효성 판정 쿼리의 조건에는 쓰지 않는다.
     * 실제 검색 대상 포함 여부는 항상 {@link #effectiveDate}(시행일)로만 판단한다
     */
    @Column(name = "confirmed_date")
    private LocalDate confirmedDate;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "auditManual", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<AuditManualItem> items = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditManual that)) return false;
        return manualId != null && manualId.equals(that.manualId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}