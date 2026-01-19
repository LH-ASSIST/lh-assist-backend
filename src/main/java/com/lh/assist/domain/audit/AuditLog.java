package com.lh.assist.domain.audit;

import com.lh.assist.common.entity.BaseTimeEntity;
import com.lh.assist.domain.user.User;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "log_id")
	private Long logId;

	@Column(name = "action_type", nullable = false, length = 50)
	private String actionType;

	@Column(name = "target_type", nullable = false, length = 50)
	private String targetType;

	@Column(name = "target_id", nullable = false)
	private Long targetId;

	@Column(name = "s3_key", nullable = false, length = 500)
	private String s3Key;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User actor;

	@Builder
	public AuditLog(
			String actionType,
			String targetType,
			Long targetId,
			String s3Key,
			User actor
	) {
		this.actionType = actionType;
		this.targetType = targetType;
		this.targetId = targetId;
		this.s3Key = s3Key;
		this.actor = actor;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AuditLog that)) {
			return false;
		}
		return logId != null && logId.equals(that.logId);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}