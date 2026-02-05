package com.lh.assist.admin.api;

import com.lh.assist.admin.api.docs.AdminApiDocs;
import com.lh.assist.common.model.ApiResponse;
import com.lh.assist.common.security.UserPrincipal;
import com.lh.assist.admin.api.docs.SuggestionAnswerDocs;
import com.lh.assist.admin.api.docs.SuggestionAdminListDocs;
import com.lh.assist.admin.api.dto.request.SuggestionAnswerRequest;
import com.lh.assist.admin.api.dto.response.AdminSuggestionListResponse;
import com.lh.assist.admin.api.mapper.AdminSuggestionMapper;
import com.lh.assist.suggestion.api.dto.response.SuggestionResponse;
import com.lh.assist.suggestion.api.mapper.SuggestionMapper;
import com.lh.assist.admin.application.AdminSuggestionService;
import com.lh.assist.suggestion.application.SuggestionViewCountService;
import com.lh.assist.suggestion.domain.entity.Suggestion;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/suggestions")
@PreAuthorize("hasRole('ADMIN')")
@AdminApiDocs
public class AdminSuggestionController {

    private final AdminSuggestionService adminSuggestionService;
    private final SuggestionViewCountService viewCountService;

    @GetMapping
    @SuggestionAdminListDocs
    public ResponseEntity<ApiResponse<Page<AdminSuggestionListResponse>>> listAllSuggestions(
            @AuthenticationPrincipal UserPrincipal principal,
            @ParameterObject Pageable pageable
    ) {
        Page<Suggestion> suggestions = adminSuggestionService.getAllSuggestions(pageable);
        List<Long> ids = suggestions.getContent().stream()
                .map(Suggestion::getSuggestionId)
                .toList();
        Map<Long, Integer> deltas = viewCountService.getViewCountDeltas(ids);
        Page<AdminSuggestionListResponse> responses = suggestions.map(suggestion -> {
            int viewCount = suggestion.getViewCount() + deltas.getOrDefault(suggestion.getSuggestionId(), 0);
            return AdminSuggestionMapper.toAdminListResponse(suggestion, principal, viewCount);
        });
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PatchMapping("/{suggestionId}/answer")
    @SuggestionAnswerDocs
    public ResponseEntity<ApiResponse<SuggestionResponse>> answerSuggestion(
            @PathVariable Long suggestionId,
            @Validated @RequestBody SuggestionAnswerRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Suggestion answered = adminSuggestionService.answerSuggestion(
                suggestionId,
                request.answerContent(),
                principal.userId()
        );
        int viewCount = viewCountService.getViewCount(suggestionId, answered.getViewCount());
        SuggestionResponse response = SuggestionMapper.toResponse(answered, principal, viewCount);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}