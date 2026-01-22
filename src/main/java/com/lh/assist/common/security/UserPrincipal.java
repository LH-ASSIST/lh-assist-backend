package com.lh.assist.common.security;

import com.lh.assist.user.domain.UserRole;

public record UserPrincipal(
        Long userId,
        String email,
        String role
) {
    public boolean isAdmin() {
        return UserRole.isAdmin(role);
    }
}