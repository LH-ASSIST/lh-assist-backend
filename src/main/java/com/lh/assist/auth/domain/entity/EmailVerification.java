package com.lh.assist.auth.domain.entity;

import com.lh.assist.auth.domain.enums.EmailVerificationPurpose;
import com.lh.assist.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "email_verifications")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EmailVerification extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "verify_id")
	private Long verifyId;

	@Column(nullable = false, length = 100)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private EmailVerificationPurpose purpose;

	@Column(name = "token_hash", nullable = false, length = 255)
	private String code;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "verified_at")
	private LocalDateTime verifiedAt;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	public boolean isExpired(LocalDateTime now) {
		return expiresAt.isBefore(now);
	}

	public void markVerified(LocalDateTime verifiedAt) {
		this.verifiedAt = verifiedAt;
	}

	public void incrementAttemptCount() {
		this.attemptCount += 1;
	}
}