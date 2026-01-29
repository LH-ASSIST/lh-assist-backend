package com.lh.assist.auth.domain.repository;

import com.lh.assist.auth.domain.entity.EmailVerification;
import com.lh.assist.auth.domain.enums.EmailVerificationPurpose;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

	Optional<EmailVerification> findTopByEmailAndPurposeOrderByCreatedAtDesc(
		String email,
		EmailVerificationPurpose purpose
	);
}