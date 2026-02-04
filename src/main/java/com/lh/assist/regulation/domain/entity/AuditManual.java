package com.lh.assist.regulation.domain.entity;

import com.lh.assist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "audit_manuals")
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
        return manualId != null ? manualId.hashCode() : getClass().hashCode();
    }
}