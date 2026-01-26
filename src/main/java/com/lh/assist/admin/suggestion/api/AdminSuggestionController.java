package com.lh.assist.admin.suggestion.api;

import com.lh.assist.common.model.ApiResponse;
import com.lh.assist.common.security.UserPrincipal;
import com.lh.assist.admin.suggestion.api.docs.SuggestionAdminListDocs;
import com.lh.assist.suggestion.api.dto.response.SuggestionListResponse;
import com.lh.assist.suggestion.api.mapper.SuggestionMapper;
import com.lh.assist.admin.suggestion.application.AdminSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/suggestions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSuggestionController {

    private final AdminSuggestionService adminSuggestionService;

    @GetMapping
    @SuggestionAdminListDocs
    public ResponseEntity<ApiResponse<Page<SuggestionListResponse>>> listAllSuggestions(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable
    ) {
        Page<SuggestionListResponse> responses = adminSuggestionService.getAllSuggestions(pageable)
                .map(suggestion -> SuggestionMapper.toListResponse(suggestion, principal));
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}