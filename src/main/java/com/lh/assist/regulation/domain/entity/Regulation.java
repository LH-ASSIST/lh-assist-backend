package com.lh.assist.regulation.domain.entity;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.regulation.domain.enums.RegulationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "regulations")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Regulation extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long regId;

	@Column(nullable = false, length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(name = "reg_type", nullable = false, length = 30)
	private RegulationType regType;

	@Column(name = "effective_date", nullable = false)
	private LocalDate effectiveDate;

	@Column(name = "expiry_date")
	private LocalDate expiryDate;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "amendment_date", nullable = false)
	private LocalDate amendmentDate;

	@Column(nullable = false, length = 100)
	private String version;

	@Column(name = "source_url", nullable = false, length = 500)
	private String sourceUrl;

	@OneToMany(mappedBy = "regulation", fetch = FetchType.LAZY)
	@Builder.Default
	private List<RegItem> items = new ArrayList<>();

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Regulation that)) {
			return false;
		}
		return regId != null && regId.equals(that.regId);
	}

	@Override
	public int hashCode() {
		return regId != null ? regId.hashCode() : getClass().hashCode();
	}
}