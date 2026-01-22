package com.lh.assist.auth.application;

import com.lh.assist.auth.api.dto.request.LoginRequest;
import com.lh.assist.auth.api.dto.response.LoginResponse;
import com.lh.assist.auth.api.dto.request.SignupRequest;
import com.lh.assist.auth.api.dto.response.SignupResponse;
import com.lh.assist.common.exception.BusinessException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.security.jwt.JwtTokenProvider;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.repository.UserRepository;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider tokenProvider;

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}

		User user = User.builder()
				.email(request.email())
				.password(passwordEncoder.encode(request.password()))
				.name(request.name())
				.department(request.department())
				.position(request.position())
				.role(UserRole.USER)
				.status(UserStatus.ACTIVE)
				.emailVerified(false)
				.attemptCount(0)
				.build();

		User saved = userRepository.save(user);
		return SignupResponse.builder()
				.userId(saved.getUserId())
				.email(saved.getEmail())
				.name(saved.getName())
				.department(saved.getDepartment())
				.position(saved.getPosition())
				.status(saved.getStatus())
				.createdAt(saved.getCreatedAt())
				.build();
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		String token = tokenProvider.createToken(user);
		return LoginResponse.builder()
				.accessToken(token)
				.tokenType("Bearer")
				.userId(user.getUserId())
				.email(user.getEmail())
				.role(user.getRole())
				.build();
	}
}
