package com.lh.assist.common.security;

import com.lh.assist.user.domain.enums.UserRole;

public record UserPrincipal(
        Long userId,
        String email,
        String role
) {
    private static final String GUEST_EMAIL_PREFIX = "guest+";
    private static final String GUEST_EMAIL_DOMAIN = "@lh-assist.local";

    public boolean isAdmin() {
        return UserRole.isAdmin(role);
    }

    public boolean isGuest() {
        if (email == null) {
            return false;
        }
        return email.startsWith(GUEST_EMAIL_PREFIX) && email.endsWith(GUEST_EMAIL_DOMAIN);
    }
}