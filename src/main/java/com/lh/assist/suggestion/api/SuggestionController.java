package com.lh.assist.suggestion.api;

import com.lh.assist.common.model.ApiResponse;
import com.lh.assist.common.security.UserPrincipal;
import com.lh.assist.suggestion.api.dto.SuggestionCreateRequest;
import com.lh.assist.suggestion.api.dto.SuggestionListResponse;
import com.lh.assist.suggestion.api.dto.SuggestionResponse;
import com.lh.assist.suggestion.api.docs.SuggestionCreateDocs;
import com.lh.assist.suggestion.api.docs.SuggestionGetDocs;
import com.lh.assist.suggestion.api.docs.SuggestionListDocs;
import com.lh.assist.suggestion.api.mapper.SuggestionMapper;
import com.lh.assist.suggestion.application.SuggestionService;
import com.lh.assist.suggestion.domain.Suggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/qna")
public class SuggestionController {

    private final SuggestionService suggestionService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @SuggestionListDocs
    public ResponseEntity<ApiResponse<Page<SuggestionListResponse>>> listSuggestions(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable
    ) {
        Page<SuggestionListResponse> responses = suggestionService.getSuggestions(
                principal.userId(),
                principal.isAdmin(),
                pageable
        )
                .map(suggestion -> SuggestionMapper.toListResponse(suggestion, principal));
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/{suggestionId}")
    @PreAuthorize("isAuthenticated()")
    @SuggestionGetDocs
    public ResponseEntity<ApiResponse<SuggestionResponse>> getSuggestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long suggestionId
    ) {
        Suggestion suggestion = suggestionService.getSuggestion(
                principal.userId(),
                principal.isAdmin(),
                suggestionId
        );
        SuggestionResponse response = SuggestionMapper.toResponse(suggestion, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @SuggestionCreateDocs
    public ResponseEntity<ApiResponse<SuggestionResponse>> createSuggestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @Validated @RequestBody SuggestionCreateRequest request
    ) {
        Suggestion created = suggestionService.createSuggestion(principal.userId(), request);
        SuggestionResponse response = SuggestionMapper.toResponse(created, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }
}
