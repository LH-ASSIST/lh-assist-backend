package com.lh.assist.auth.api.mapper;

import com.lh.assist.auth.api.dto.response.LoginResponse;
import com.lh.assist.auth.api.dto.response.RefreshResponse;
import com.lh.assist.auth.api.dto.response.SendEmailVerificationResponse;
import com.lh.assist.auth.api.dto.response.SignupResponse;
import com.lh.assist.auth.domain.enums.EmailVerificationPurpose;
import com.lh.assist.common.security.jwt.TokenPair;
import com.lh.assist.user.domain.entity.User;
import java.time.LocalDateTime;

public final class AuthMapper {

    private AuthMapper() {
    }

    public static SignupResponse toSignupResponse(User user) {
        return SignupResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .department(user.getDepartment())
                .position(user.getPosition())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public static LoginResponse toLoginResponse(
            TokenPair tokenPair,
            User user
    ) {
        return LoginResponse.builder()
                .accessToken(tokenPair.accessToken())
                .refreshToken(tokenPair.refreshToken())
                .tokenType("Bearer")
                .userId(user.getUserId())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    public static RefreshResponse toRefreshResponse(TokenPair tokenPair) {
        return RefreshResponse.builder()
                .accessToken(tokenPair.accessToken())
                .refreshToken(tokenPair.refreshToken())
                .tokenType("Bearer")
                .build();
    }

    public static SendEmailVerificationResponse toSendEmailVerificationResponse(
            String email,
            EmailVerificationPurpose purpose,
            LocalDateTime expiresAt
    ) {
        return SendEmailVerificationResponse.builder()
                .email(email)
                .purpose(purpose)
                .expiresAt(expiresAt)
                .build();
    }
}