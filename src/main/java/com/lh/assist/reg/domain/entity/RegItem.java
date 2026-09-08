package com.lh.assist.reg.domain.entity;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.reg.domain.VectorStringConverter;
import com.lh.assist.reg.domain.enums.RegChangeType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
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
	@Column(name = "item_id")
	private Long itemId;

	@Column(name = "clause_number", nullable = false, length = 50)
	private String clauseNumber;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
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
	@JdbcTypeCode(SqlTypes.OTHER)
	@Column(name = "vector_index", columnDefinition = "vector(1536)")
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

	/**
	 * 이 조문만 별도로 개정된 경우의 발효일
	 *
	 * null이면 소속 규정({@link Regulation#getEffectiveDate()})과 같은 시점에 발효된
	 * 것으로 간주한다. 조문 대부분은 규정 전체와 함께 발효되므로 기본값은 null이다
	 */
	@Column(name = "effective_date")
	private LocalDate effectiveDate;

	/**
	 * 이 조문만 별도로 만료/개정된 경우의 만료일
	 *
	 * null이면 소속 규정({@link Regulation#getExpiryDate()})을 그대로 물려받는다
	 */
	@Column(name = "expiry_date")
	private LocalDate expiryDate;

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