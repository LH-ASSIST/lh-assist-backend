package com.lh.assist.user.api;

import com.lh.assist.common.model.ApiResponse;
import com.lh.assist.common.security.UserPrincipal;
import com.lh.assist.user.api.docs.UserApiDocs;
import com.lh.assist.user.api.docs.UserMyPageDocs;
import com.lh.assist.user.api.docs.UserPasswordChangeDocs;
import com.lh.assist.user.api.docs.UserPasswordResetDocs;
import com.lh.assist.user.api.dto.request.UserPasswordChangeRequest;
import com.lh.assist.user.api.dto.request.UserPasswordResetRequest;
import com.lh.assist.user.api.dto.response.UserMyPageResponse;
import com.lh.assist.user.api.mapper.UserMapper;
import com.lh.assist.user.application.UserService;
import com.lh.assist.user.domain.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user")
@UserApiDocs
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @UserMyPageDocs
    public ResponseEntity<ApiResponse<UserMyPageResponse>> getMyPage(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        User user = userService.getMyPage(principal.userId());
        UserMyPageResponse response = UserMapper.toMyPageResponse(user);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/password/reset")
    @UserPasswordResetDocs
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody UserPasswordResetRequest request
    ) {
        userService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    @UserPasswordChangeDocs
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserPasswordChangeRequest request
    ) {
        userService.changePassword(principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}