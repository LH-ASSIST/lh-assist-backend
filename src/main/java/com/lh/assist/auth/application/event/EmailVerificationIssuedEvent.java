package com.lh.assist.auth.application.event;

public record EmailVerificationIssuedEvent(
        String email,
        String code
) {
}