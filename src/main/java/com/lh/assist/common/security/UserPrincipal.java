package com.lh.assist.common.security;

public record UserPrincipal(
        Long userId,
        String email,
        String role
) {
}