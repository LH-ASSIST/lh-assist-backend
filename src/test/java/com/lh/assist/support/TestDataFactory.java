package com.lh.assist.support;

import com.lh.assist.suggestion.domain.entity.Suggestion;
import com.lh.assist.suggestion.domain.enums.SuggestionCategory;
import com.lh.assist.notice.domain.entity.Notice;
import com.lh.assist.user.domain.entity.User;
import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserPosition;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;

public final class TestDataFactory {

    private TestDataFactory() {
    }

    public static User user(String email) {
        return User.builder()
                .email(email)
                .password("hashed")
                .name("Tester")
                .department(UserDepartment.PUBLIC_HOUSING_BUSINESS_OFFICE)
                .position(UserPosition.STAFF)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .attemptCount(0)
                .build();
    }

    public static User admin(String email) {
        return User.builder()
                .email(email)
                .password("hashed")
                .name("Admin")
                .department(UserDepartment.ETC)
                .position(UserPosition.ETC)
                .role(UserRole.ADMIN)
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

    public static Notice notice(String title, String content) {
        return Notice.builder()
                .title(title)
                .content(content)
                .build();
    }
}