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
        return userWith(
                email,
                "hashed",
                "Tester",
                UserRole.USER,
                UserDepartment.PUBLIC_HOUSING_BUSINESS_OFFICE,
                UserPosition.STAFF,
                UserStatus.ACTIVE,
                true
        );
    }

    public static User admin(String email) {
        return userWith(
                email,
                "hashed",
                "Admin",
                UserRole.ADMIN,
                UserDepartment.ETC,
                UserPosition.ETC,
                UserStatus.ACTIVE,
                true
        );
    }

    public static User userWith(
            String email,
            String password,
            String name,
            UserRole role,
            UserDepartment department,
            UserPosition position,
            UserStatus status,
            boolean emailVerified
    ) {
        return User.builder()
                .email(email)
                .password(password)
                .name(name)
                .department(department)
                .position(position)
                .role(role)
                .status(status)
                .emailVerified(emailVerified)
                .attemptCount(0)
                .build();
    }

    public static User userWithId(
            String email,
            Long userId
    ) {
        User user = user(email);
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
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

    public static Notice notice(
            String title,
            String content
    ) {
        return Notice.builder()
                .title(title)
                .content(content)
                .build();
    }
}