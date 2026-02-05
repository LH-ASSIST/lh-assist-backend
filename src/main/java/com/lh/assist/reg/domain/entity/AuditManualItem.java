package com.lh.assist.reg.domain.entity;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.reg.domain.VectorStringConverter;
import com.lh.assist.reg.domain.enums.ManualContentType;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Table(
        name = "audit_manual_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_manual_item",
                        columnNames = {"manual_id", "article_name", "section_number", "content_type"}
                )
        }
)
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditManualItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "manual_item_id")
    private Long manualItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manual_id", nullable = false)
    private AuditManual auditManual;

    @Column(name = "article_name", length = 100)
    private String articleName;

    @Column(name = "section_number")
    private Integer sectionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", length = 30)
    private ManualContentType contentType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Convert(converter = VectorStringConverter.class)
    @Column(name = "vector_index", columnDefinition = "vector(1536)")
    private float[] embedding;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditManualItem that)) return false;
        return manualItemId != null && manualItemId.equals(that.manualItemId);
    }

    @Override
    public int hashCode() {
        return manualItemId != null ? manualItemId.hashCode() : getClass().hashCode();
    }
}