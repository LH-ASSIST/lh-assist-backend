package com.lh.assist.support;

import com.lh.assist.suggestion.domain.Suggestion;
import com.lh.assist.suggestion.domain.SuggestionCategory;
import com.lh.assist.user.domain.User;
import com.lh.assist.user.domain.UserDepartment;
import com.lh.assist.user.domain.UserPosition;
import com.lh.assist.user.domain.UserRole;
import com.lh.assist.user.domain.UserStatus;

public final class TestDataFactory {

    private TestDataFactory() {
    }

    public static User user(
            String email,
            UserRole role
    ) {
        return User.builder()
                .email(email)
                .password("hashed")
                .name("Tester")
                .department(UserDepartment.PUBLIC_HOUSING_HEADQUARTERS)
                .position(UserPosition.DEPUTY_MANAGER)
                .role(role)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .attemptCount(0)
                .build();
    }

    public static Suggestion suggestion(
            User owner,
            boolean isPrivate
    ) {
        return Suggestion.builder()
                .title("제목")
                .content("내용")
                .category(SuggestionCategory.SYSTEM_ERROR)
                .isPrivate(isPrivate)
                .isAnonymous(true)
                .user(owner)
                .build();
    }
}