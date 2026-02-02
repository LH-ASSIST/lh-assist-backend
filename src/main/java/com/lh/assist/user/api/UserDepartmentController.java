package com.lh.assist.user.api;

import com.lh.assist.common.model.ApiResponse;
import com.lh.assist.common.security.UserPrincipal;
import com.lh.assist.user.api.docs.UserDepartmentListDocs;
import com.lh.assist.user.api.dto.response.UserListResponse;
import com.lh.assist.user.api.mapper.UserMapper;
import com.lh.assist.user.application.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user/department")
public class UserDepartmentController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @UserDepartmentListDocs
    public ResponseEntity<ApiResponse<List<UserListResponse>>> getUsersByDepartment(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<UserListResponse> responses = userService.getUsersByDepartment(principal.userId())
                .stream()
                .map(UserMapper::toListResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}