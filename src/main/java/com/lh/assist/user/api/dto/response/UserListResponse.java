package com.lh.assist.user.api.dto.response;

import com.lh.assist.user.domain.enums.UserDepartment;
import com.lh.assist.user.domain.enums.UserPosition;
import com.lh.assist.user.domain.enums.UserRole;
import com.lh.assist.user.domain.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserListResponse {
    private final Long userId;
    private final String email;
    private final String name;
    private final UserRole role;
    private final UserPosition position;
    private final UserDepartment department;
    private final UserStatus status;
}