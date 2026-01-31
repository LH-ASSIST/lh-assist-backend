package com.lh.assist.user.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserPasswordResetRequest(
        @NotBlank
        @Email
        String email
) {
}