package com.lh.assist.reg.domain.entity;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.reg.domain.enums.RegulationType;
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
import org.hibernate.annotations.Check;

@Getter
@Entity
@Table(name = "regulations")
@Check(name = "chk_regulations_confirmed_before_effective",
		constraints = "confirmed_date IS NULL OR confirmed_date <= effective_date")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Regulation extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "reg_id")
	private Long regId;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(name = "reg_type", nullable = false, length = 30)
	private RegulationType regType;

	@Column(name = "effective_date", nullable = false)
	private LocalDate effectiveDate;

	@Column(name = "expiry_date")
	private LocalDate expiryDate;

	/**
	 * 확정일(공포/의결일)
	 *
	 * 기록용 메타데이터일 뿐 유효성 판정 쿼리의 조건에는 쓰지 않는다.
	 * 실제 검색 대상 포함 여부는 항상 {@link #effectiveDate}(시행일)로만 판단한다
	 */
	@Column(name = "confirmed_date")
	private LocalDate confirmedDate;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "amendment_date", nullable = false)
	private LocalDate amendmentDate;

	@Column(name = "version", nullable = false, length = 100)
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