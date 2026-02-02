package com.lh.assist.admin.user.api;

import com.lh.assist.common.model.ApiResponse;
import com.lh.assist.common.security.UserPrincipal;
import com.lh.assist.user.api.docs.UserAdminListDocs;
import com.lh.assist.user.api.dto.response.UserListResponse;
import com.lh.assist.user.api.mapper.UserMapper;
import com.lh.assist.user.application.UserService;
import com.lh.assist.user.domain.enums.UserDepartment;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @UserAdminListDocs
    public ResponseEntity<ApiResponse<List<UserListResponse>>> getUsers(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "department", required = false) UserDepartment department
    ) {
        List<UserListResponse> responses = userService.getAllUsersForAdmin(
                principal.userId(),
                department
        ).stream().map(UserMapper::toListResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}