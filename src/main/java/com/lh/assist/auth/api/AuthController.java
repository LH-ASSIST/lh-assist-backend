package com.lh.assist.auth.api;

import com.lh.assist.auth.api.docs.AuthApiDocs;
import com.lh.assist.auth.api.docs.AuthLoginDocs;
import com.lh.assist.auth.api.docs.AuthSignupDocs;
import com.lh.assist.auth.api.docs.AuthLogoutDocs;
import com.lh.assist.auth.api.docs.AuthRefreshDocs;
import com.lh.assist.auth.api.docs.AuthEmailVerificationSendDocs;
import com.lh.assist.auth.api.docs.AuthEmailVerificationVerifyDocs;
import com.lh.assist.auth.api.dto.request.LoginRequest;
import com.lh.assist.auth.api.dto.request.LogoutRequest;
import com.lh.assist.auth.api.dto.request.RefreshRequest;
import com.lh.assist.auth.api.dto.response.LoginResponse;
import com.lh.assist.auth.api.dto.response.RefreshResponse;
import com.lh.assist.auth.api.dto.request.SignupRequest;
import com.lh.assist.auth.api.dto.response.SignupResponse;
import com.lh.assist.auth.api.dto.request.SendEmailVerificationRequest;
import com.lh.assist.auth.api.dto.request.VerifyEmailRequest;
import com.lh.assist.auth.api.dto.response.SendEmailVerificationResponse;
import com.lh.assist.auth.application.AuthService;
import com.lh.assist.auth.application.EmailVerificationService;
import com.lh.assist.common.exception.AuthException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.common.model.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@AuthApiDocs
public class AuthController {

	private final AuthService authService;
	private final EmailVerificationService emailVerificationService;

	@PostMapping("/signup")
	@AuthSignupDocs
	public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
		SignupResponse response = authService.signup(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
	}

	@PostMapping("/login")
	@AuthLoginDocs
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		LoginResponse response = authService.login(request);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@PostMapping("/guest")
	public ResponseEntity<ApiResponse<LoginResponse>> guestLogin() {
		LoginResponse response = authService.issueGuestToken();
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@PostMapping("/email/verification/send")
	@AuthEmailVerificationSendDocs
	public ResponseEntity<ApiResponse<SendEmailVerificationResponse>> sendEmailVerification(
			@Valid @RequestBody SendEmailVerificationRequest request
	) {
		SendEmailVerificationResponse response = emailVerificationService
			.sendVerification(request.email(), request.purpose());
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@PostMapping("/email/verification/verify")
	@AuthEmailVerificationVerifyDocs
	public ResponseEntity<ApiResponse<Void>> verifyEmail(
			@Valid @RequestBody VerifyEmailRequest request
	) {
		emailVerificationService.verifyCode(request.email(), request.purpose(), request.code());
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@PostMapping("/refresh")
	@AuthRefreshDocs
	public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
		RefreshResponse response = authService.refresh(request.refreshToken());
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@PostMapping("/logout")
	@AuthLogoutDocs
	public ResponseEntity<ApiResponse<Void>> logout(
			@Valid @RequestBody LogoutRequest request,
			jakarta.servlet.http.HttpServletRequest httpRequest
	) {
		String accessToken = extractAccessToken(httpRequest.getHeader(HttpHeaders.AUTHORIZATION));
		authService.logout(request.refreshToken(), accessToken);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	private String extractAccessToken(String header) {
		if (header == null || header.isBlank()) {
			throw new AuthException(ErrorCode.UNAUTHORIZED);
		}
		if (!header.startsWith("Bearer ")) {
			throw new AuthException(ErrorCode.UNAUTHORIZED);
		}
		String token = header.substring(7);
		if (token.isBlank()) {
			throw new AuthException(ErrorCode.UNAUTHORIZED);
		}
		return token;
	}
}