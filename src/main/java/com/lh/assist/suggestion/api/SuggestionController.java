package com.lh.assist.suggestion.api;

import com.lh.assist.common.model.ApiResponse;
import com.lh.assist.common.security.UserPrincipal;
import com.lh.assist.suggestion.api.dto.request.SuggestionCreateRequest;
import com.lh.assist.suggestion.api.dto.response.SuggestionListResponse;
import com.lh.assist.suggestion.api.dto.response.SuggestionResponse;
import com.lh.assist.suggestion.api.docs.SuggestionCreateDocs;
import com.lh.assist.suggestion.api.docs.SuggestionDeleteDocs;
import com.lh.assist.suggestion.api.docs.SuggestionGetDocs;
import com.lh.assist.suggestion.api.docs.SuggestionListDocs;
import com.lh.assist.suggestion.api.docs.SuggestionUpdateDocs;
import com.lh.assist.suggestion.api.mapper.SuggestionMapper;
import com.lh.assist.suggestion.api.dto.request.SuggestionUpdateRequest;
import com.lh.assist.suggestion.application.SuggestionService;
import com.lh.assist.suggestion.domain.entity.Suggestion;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/qna")
public class SuggestionController {

    private final SuggestionService suggestionService;

    @GetMapping("/all")
    @SuggestionListDocs
    public ResponseEntity<ApiResponse<Page<SuggestionListResponse>>> listSuggestions(
            @AuthenticationPrincipal UserPrincipal principal,
            @ParameterObject Pageable pageable
    ) {
        Page<Suggestion> suggestions = suggestionService.getSuggestions(
                principal != null ? principal.userId() : null,
                principal != null && principal.isAdmin(),
                pageable
        );
        List<Long> ids = suggestions.getContent().stream()
                .map(Suggestion::getSuggestionId)
                .toList();
        Map<Long, Integer> deltas = suggestionService.getViewCountDeltas(ids);
        Page<SuggestionListResponse> responses = suggestions.map(suggestion -> {
            int viewCount = suggestion.getViewCount() + deltas.getOrDefault(suggestion.getSuggestionId(), 0);
            return SuggestionMapper.toListResponse(suggestion, principal, viewCount);
        });
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
        int viewCount = suggestionService.getViewCount(suggestionId, suggestion.getViewCount());
        SuggestionResponse response = SuggestionMapper.toResponse(suggestion, principal, viewCount);
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

    @PutMapping("/{suggestionId}")
    @PreAuthorize("isAuthenticated()")
    @SuggestionUpdateDocs
    public ResponseEntity<ApiResponse<SuggestionResponse>> updateSuggestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long suggestionId,
            @Validated @RequestBody SuggestionUpdateRequest request
    ) {
        Suggestion updated = suggestionService.updateSuggestion(
                principal.userId(),
                principal.isAdmin(),
                suggestionId,
                request
        );
        int viewCount = suggestionService.getViewCount(suggestionId, updated.getViewCount());
        SuggestionResponse response = SuggestionMapper.toResponse(updated, principal, viewCount);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{suggestionId}")
    @PreAuthorize("isAuthenticated()")
    @SuggestionDeleteDocs
    public ResponseEntity<ApiResponse<Void>> deleteSuggestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long suggestionId
    ) {
        suggestionService.deleteSuggestion(principal.userId(), principal.isAdmin(), suggestionId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.noContent());
    }
}
