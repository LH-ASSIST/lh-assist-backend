package com.lh.assist.domain.user;

import com.lh.assist.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long userId;

	@Column(nullable = false, unique = true, length = 100)
	private String email;

	@Column()
	private String password;

	@Column(nullable = false, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserPosition position;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private UserDepartment department;

	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof User that)) {
			return false;
		}
		return userId != null && userId.equals(that.userId);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}
