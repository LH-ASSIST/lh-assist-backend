package com.lh.assist.auth.application;

import com.lh.assist.auth.api.dto.request.LoginRequest;
import com.lh.assist.auth.api.dto.request.SignupRequest;
import com.lh.assist.auth.api.dto.response.LoginResponse;
import com.lh.assist.auth.api.dto.response.RefreshResponse;
import com.lh.assist.auth.api.dto.response.SignupResponse;
import com.lh.assist.auth.api.mapper.AuthMapper;
import com.lh.assist.auth.domain.entity.EmailVerification;
import com.lh.assist.auth.domain.enums.EmailVerificationPurpose;
import com.lh.assist.auth.domain.repository.EmailVerificationRepository;
import com.lh.assist.common.exception.AuthException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.security.jwt.TokenPair;
import com.lh.assist.common.security.jwt.TokenService;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	private final EmailVerificationRepository emailVerificationRepository;

	/**
	 * 회원가입 요청을 처리하고 신규 사용자를 저장한다
	 *
	 * 이미 등록된 이메일이면 예외를 발생시킨다
	 *
	 * @param request 회원가입 요청 정보
	 * @return 저장된 사용자 정보
	 */
	@Transactional
	public SignupResponse signup(SignupRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new AuthException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		ensureEmailVerifiedForSignup(request.email());

		User user = User.builder()
				.email(request.email())
				.password(passwordEncoder.encode(request.password()))
				.name(request.name())
				.department(request.department())
				.position(request.position())
				.role(UserRole.USER)
				.status(UserStatus.ACTIVE)
				.emailVerified(true)
				.attemptCount(0)
				.build();

		User saved = userRepository.save(user);
		return AuthMapper.toSignupResponse(saved);
	}

	private void ensureEmailVerifiedForSignup(String email) {
		EmailVerification verification = emailVerificationRepository
			.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, EmailVerificationPurpose.SIGNUP)
			.orElseThrow(() -> new AuthException(ErrorCode.EMAIL_NOT_VERIFIED));

		LocalDateTime now = LocalDateTime.now();
		if (verification.getVerifiedAt() == null) {
			throw new AuthException(ErrorCode.EMAIL_NOT_VERIFIED);
		}
		if (verification.isExpired(now)) {
			throw new AuthException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
		}
	}

	/**
	 * 로그인 요청을 검증하고 액세스 토큰을 발급한다
	 *
	 * 이메일 또는 비밀번호가 일치하지 않으면 예외를 발생시킨다
	 *
	 * @param request 로그인 요청 정보
	 * @return 로그인 응답 정보
	 */
	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));

		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new AuthException(ErrorCode.INVALID_CREDENTIALS);
		}
		if (!user.isEmailVerified()) {
			throw new AuthException(ErrorCode.EMAIL_NOT_VERIFIED);
		}

		TokenPair tokenPair = tokenService.issueLoginTokens(user);
		return AuthMapper.toLoginResponse(tokenPair, user);
	}

	@Transactional(readOnly = true)
	public RefreshResponse refresh(String refreshToken) {
		TokenPair tokenPair = tokenService.rotateRefreshToken(refreshToken);
		return AuthMapper.toRefreshResponse(tokenPair);
	}

	@Transactional
	public void logout(
			String refreshToken,
			String accessToken
	) {
		tokenService.logout(refreshToken, accessToken);
	}
}
