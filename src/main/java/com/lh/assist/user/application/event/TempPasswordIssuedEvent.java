package com.lh.assist.user.application.event;

public record TempPasswordIssuedEvent(
        String email,
        String tempPassword
) {
}