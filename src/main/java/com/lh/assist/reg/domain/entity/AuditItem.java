package com.lh.assist.reg.domain.entity;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.reg.domain.VectorStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "audit_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_audit_doc_chunk",
                        columnNames = {"doc_id", "chunk_index"}
                )
        }
)
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "doc_id", nullable = false, length = 100)
    private String docId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "doc_title", length = 200)
    private String docTitle;

    @Column(name = "source_path", length = 500)
    private String sourcePath;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Convert(converter = VectorStringConverter.class)
    @JdbcTypeCode(SqlTypes.OTHER)
    @Column(name = "vector_index", columnDefinition = "vector(1536)")
    private float[] embedding;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditItem that)) return false;
        return itemId != null && itemId.equals(that.itemId);
    }

    @Override
    public int hashCode() {
        return itemId != null ? itemId.hashCode() : getClass().hashCode();
    }
}