package com.lh.assist.user.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UserPasswordChangeRequest(
        @NotBlank
        String currentPassword,
        @NotBlank
        String newPassword
) {
}