package com.lh.assist.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lh.assist.auth.api.dto.request.LoginRequest;
import com.lh.assist.auth.api.dto.response.LoginResponse;
import com.lh.assist.auth.api.dto.request.SignupRequest;
import com.lh.assist.auth.api.dto.response.SignupResponse;
import com.lh.assist.common.exception.BusinessException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.security.jwt.JwtTokenProvider;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserPosition;
import com.lh.assist.user.domain.repository.UserRepository;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtTokenProvider tokenProvider;

	@InjectMocks
	private AuthService authService;

	@Test
	@DisplayName("회원가입이 성공하면 저장된 사용자 정보를 반환해야 한다")
	void 회원가입_성공하면_저장된_사용자_반환() {
		SignupRequest request = signupRequest(
			"new@lh.com"
		);

		when(userRepository.existsByEmail("new@lh.com")).thenReturn(false);
		when(passwordEncoder.encode("Test1234!")).thenReturn("hashed");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User input = invocation.getArgument(0);
			return User.builder()
				.userId(1L)
				.email(input.getEmail())
				.password(input.getPassword())
				.name(input.getName())
				.department(input.getDepartment())
				.position(input.getPosition())
				.role(input.getRole())
				.status(input.getStatus())
				.emailVerified(input.isEmailVerified())
				.attemptCount(input.getAttemptCount())
				.build();
		});

		SignupResponse response = authService.signup(request);

		assertThat(response.getUserId()).isEqualTo(1L);
		assertThat(response.getEmail()).isEqualTo("new@lh.com");
		assertThat(response.getName()).isEqualTo("Tester");
	}

	@Test
	@DisplayName("이미 사용 중인 이메일이면 예외가 발생해야 한다")
	void 이미_사용중인_이메일이면_예외_발생() {
		SignupRequest request = signupRequest(
			"exists@lh.com"
		);

		when(userRepository.existsByEmail("exists@lh.com")).thenReturn(true);

		assertThatThrownBy(() -> authService.signup(request))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
	}

	@Test
	@DisplayName("로그인이 성공하면 토큰과 사용자 정보를 반환해야 한다")
	void 로그인_성공하면_토큰과_사용자_반환() {
		LoginRequest request = loginRequest("Test1234!");

		User user = User.builder()
			.userId(10L)
			.email("login@lh.com")
			.password("hashed")
			.name("Tester")
			.department(UserDepartment.PUBLIC_HOUSING_HEADQUARTERS)
			.position(UserPosition.TEAM_LEAD)
			.role(UserRole.USER)
			.status(UserStatus.ACTIVE)
			.emailVerified(false)
			.attemptCount(0)
			.build();

		when(userRepository.findByEmail("login@lh.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("Test1234!", "hashed")).thenReturn(true);
		when(tokenProvider.createToken(user)).thenReturn("token");

		LoginResponse response = authService.login(request);

		assertThat(response.getAccessToken()).isEqualTo("token");
		assertThat(response.getEmail()).isEqualTo("login@lh.com");
		assertThat(response.getRole()).isEqualTo(UserRole.USER);
	}

	@Test
	@DisplayName("비밀번호가 틀리면 예외가 발생해야 한다")
	void 비밀번호가_틀리면_예외_발생() {
		LoginRequest request = loginRequest("bad");

		User user = User.builder()
			.userId(10L)
			.email("login@lh.com")
			.password("hashed")
			.name("Tester")
			.department(UserDepartment.PUBLIC_HOUSING_HEADQUARTERS)
			.position(UserPosition.TEAM_LEAD)
			.role(UserRole.USER)
			.status(UserStatus.ACTIVE)
			.emailVerified(false)
			.attemptCount(0)
			.build();

		when(userRepository.findByEmail("login@lh.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("bad", "hashed")).thenReturn(false);

		assertThatThrownBy(() -> authService.login(request))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_CREDENTIALS);
	}

	@Test
	@DisplayName("사용자를 찾을 수 없으면 인증 오류가 발생해야 한다")
	void 사용자_없으면_인증_오류() {
		LoginRequest request = loginRequest("Test1234!");
		when(userRepository.findByEmail("login@lh.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(request))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_CREDENTIALS);
	}

	private static SignupRequest signupRequest(
		String email
	) {
		return new SignupRequest(
			email,
			"Test1234!",
			"Tester",
			UserDepartment.PUBLIC_HOUSING_HEADQUARTERS,
			UserPosition.STAFF
		);
	}

	private static LoginRequest loginRequest(String password) {
		return new LoginRequest("login@lh.com", password);
	}
}
