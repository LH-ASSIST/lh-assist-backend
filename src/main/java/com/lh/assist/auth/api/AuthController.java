package com.lh.assist.auth.api;

import com.lh.assist.auth.api.docs.AuthApiDocs;
import com.lh.assist.auth.api.docs.AuthLoginDocs;
import com.lh.assist.auth.api.docs.AuthSignupDocs;
import com.lh.assist.auth.api.dto.LoginRequest;
import com.lh.assist.auth.api.dto.LoginResponse;
import com.lh.assist.auth.api.dto.SignupRequest;
import com.lh.assist.auth.api.dto.SignupResponse;
import com.lh.assist.auth.application.AuthService;
import com.lh.assist.common.model.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
}