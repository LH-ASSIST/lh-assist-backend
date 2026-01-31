package com.lh.assist.user.api.mapper;

import com.lh.assist.user.api.dto.response.UserMyPageResponse;
import com.lh.assist.user.domain.entity.User;

public class UserMapper {

    private UserMapper() {
    }

    public static UserMyPageResponse toMyPageResponse(User user) {
        return UserMyPageResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .position(user.getPosition())
                .department(user.getDepartment())
                .status(user.getStatus())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}