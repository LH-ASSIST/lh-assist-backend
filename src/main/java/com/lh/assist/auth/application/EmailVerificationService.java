package com.lh.assist.auth.application;

import com.lh.assist.auth.api.dto.response.SendEmailVerificationResponse;
import com.lh.assist.auth.api.mapper.AuthMapper;
import com.lh.assist.auth.domain.entity.EmailVerification;
import com.lh.assist.auth.domain.enums.EmailVerificationPurpose;
import com.lh.assist.auth.application.event.EmailVerificationIssuedEvent;
import com.lh.assist.auth.domain.repository.EmailVerificationRepository;
import com.lh.assist.common.exception.AuthException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.user.domain.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String RESEND_KEY_PREFIX = "email:verification:resend";

	private final UserRepository userRepository;
	private final EmailVerificationRepository emailVerificationRepository;
	private final StringRedisTemplate stringRedisTemplate;
	private final ApplicationEventPublisher eventPublisher;

	@Value("${app.email.verification.ttl-minutes:5}")
	private long ttlMinutes;

	@Value("${app.email.verification.resend-limit:5}")
	private int resendLimit;

	/**
	 * 이메일 인증 코드를 생성하여 발송하고 저장한다
	 *
	 * 회원가입/이메일 변경은 미가입 이메일만 허용하고,
	 * 비밀번호 재설정은 기존 사용자만 허용한다
	 *
	 * @param email 인증 대상 이메일
	 * @param purpose 인증 목적
	 * @return 발송된 인증 정보
	 */
	@Transactional
	public SendEmailVerificationResponse sendVerification(
			String email,
			EmailVerificationPurpose purpose
	) {
		if (purpose == EmailVerificationPurpose.SIGNUP || purpose == EmailVerificationPurpose.EMAIL_CHANGE) {
			if (userRepository.existsByEmail(email)) {
				throw new AuthException(ErrorCode.EMAIL_ALREADY_EXISTS);
			}
		} else {
			userRepository.findByEmail(email)
				.orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND));
		}

		enforceResendLimit(email, purpose);

		String code = generateCode();
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime expiresAt = now.plusMinutes(ttlMinutes);

		EmailVerification verification = EmailVerification.builder()
			.email(email)
			.purpose(purpose)
			.code(code)
			.expiresAt(expiresAt)
			.verifiedAt(null)
			.attemptCount(0)
			.build();
		emailVerificationRepository.save(verification);

		eventPublisher.publishEvent(new EmailVerificationIssuedEvent(email, code));

		return AuthMapper.toSendEmailVerificationResponse(email, purpose, expiresAt);
	}

	/**
	 * 사용자가 입력한 인증 코드를 검증하고 인증 완료 처리한다
	 *
	 * 만료/중복 인증/코드 불일치에 따라 예외를 발생시킨다
	 *
	 * @param email 인증 대상 이메일
	 * @param purpose 인증 목적
	 * @param code 사용자가 입력한 인증 코드
	 */
	@Transactional
	public void verifyCode(
			String email,
			EmailVerificationPurpose purpose,
			String code
	) {
		EmailVerification verification = emailVerificationRepository
			.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
			.orElseThrow(() -> new AuthException(ErrorCode.EMAIL_VERIFICATION_INVALID_CODE));

		LocalDateTime now = LocalDateTime.now();
		if (verification.getVerifiedAt() != null) {
			throw new AuthException(ErrorCode.EMAIL_ALREADY_VERIFIED);
		}
		if (verification.isExpired(now)) {
			throw new AuthException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
		}

		if (!verification.getCode().equals(code)) {
			verification.incrementAttemptCount();
			emailVerificationRepository.save(verification);
			throw new AuthException(ErrorCode.EMAIL_VERIFICATION_INVALID_CODE);
		}

		verification.markVerified(now);

		stringRedisTemplate.delete(resendKey(email, purpose));
	}

	/**
	 * 동일 이메일/목적에 대한 재발송 횟수를 제한한다
	 *
	 * 제한을 초과하면 예외를 발생시킨다
	 *
	 * @param email 인증 대상 이메일
	 * @param purpose 인증 목적
	 */
	private void enforceResendLimit(
			String email,
			EmailVerificationPurpose purpose
	) {
		String key = resendKey(email, purpose);
		Long count = stringRedisTemplate.opsForValue().increment(key);
		if (count != null && count == 1L) {
			stringRedisTemplate.expire(key, Duration.ofMinutes(ttlMinutes));
		}
		if (count != null && count > resendLimit) {
			throw new AuthException(ErrorCode.EMAIL_VERIFICATION_RESEND_LIMIT);
		}
	}

	/**
	 * 6자리 숫자 인증 코드를 생성한다.
	 *
	 * @return 생성된 인증 코드
	 */
	private String generateCode() {
		int value = RANDOM.nextInt(1_000_000);
		return String.format("%06d", value);
	}

	/**
	 * 재발송 제한을 위한 Redis 키를 생성한다
	 *
	 * @param email 인증 대상 이메일
	 * @param purpose 인증 목적
	 * @return Redis 키
	 */
	private String resendKey(
			String email,
			EmailVerificationPurpose purpose
	) {
		return RESEND_KEY_PREFIX + ":" + purpose + ":" + email;
	}
}