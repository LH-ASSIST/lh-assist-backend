package com.lh.assist.regulation.domain.entity;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.regulation.domain.VectorStringConverter;
import com.lh.assist.regulation.domain.enums.RegChangeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "reg_items")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RegItem extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long itemId;

	@Column(name = "clause_number", nullable = false, length = 50)
	private String clauseNumber;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "chunk_index")
	private Integer chunkIndex;

	@Column(name = "page_number")
	private Integer pageNumber;

	@Column(name = "section_title", length = 200)
	private String sectionTitle;

	@Column(name = "content_hash", length = 64, nullable = false)
	private String contentHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "change_type", nullable = false, length = 30)
	private RegChangeType changeType;

	@Column(name = "is_mandatory", nullable = false)
	private boolean mandatory;

	@Column(name = "embedding_model", length = 100)
	private String embeddingModel;

	@Convert(converter = VectorStringConverter.class)
	@Column(name = "vector_index", columnDefinition = "vector")
	private float[] embedding;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private RegItem parent;

	@OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
	@Builder.Default
	private List<RegItem> children = new ArrayList<>();

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reg_id", nullable = false)
	private Regulation regulation;

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof RegItem that)) {
			return false;
		}
		return itemId != null && itemId.equals(that.itemId);
	}

	@Override
	public int hashCode() {
		return itemId != null ? itemId.hashCode() : getClass().hashCode();
	}
}