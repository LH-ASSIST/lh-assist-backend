package com.lh.assist.suggestion.api.mapper;

import com.lh.assist.common.security.UserPrincipal;
import com.lh.assist.suggestion.api.dto.SuggestionListResponse;
import com.lh.assist.suggestion.api.dto.SuggestionResponse;
import com.lh.assist.suggestion.domain.Suggestion;
import com.lh.assist.user.domain.User;

public final class SuggestionMapper {
    private SuggestionMapper() {
    }

    public static SuggestionResponse toResponse(
            Suggestion suggestion,
            UserPrincipal viewer
    ) {
        boolean canViewAuthor = canViewAuthor(viewer, suggestion);
        return SuggestionResponse.builder()
                .suggestionId(suggestion.getSuggestionId())
                .title(suggestion.getTitle())
                .content(suggestion.getContent())
                .category(suggestion.getCategory())
                .status(suggestion.getStatus())
                .isPrivate(suggestion.isPrivate())
                .answerContent(suggestion.getAnswerContent())
                .answeredAt(suggestion.getAnsweredAt())
                .viewCount(suggestion.getViewCount())
                .isAnonymous(suggestion.isAnonymous())
                .writerDisplay(buildWriterDisplay(suggestion, canViewAuthor))
                .userId(canViewAuthor ? extractUserId(suggestion) : null)
                .createdAt(suggestion.getCreatedAt())
                .updatedAt(suggestion.getUpdatedAt())
                .build();
    }

    public static SuggestionListResponse toListResponse(
            Suggestion suggestion,
            UserPrincipal viewer
    ) {
        boolean canViewAuthor = canViewAuthor(viewer, suggestion);
        return SuggestionListResponse.builder()
                .suggestionId(suggestion.getSuggestionId())
                .title(suggestion.getTitle())
                .category(suggestion.getCategory())
                .status(suggestion.getStatus())
                .isPrivate(suggestion.isPrivate())
                .viewCount(suggestion.getViewCount())
                .writerDisplay(buildWriterDisplay(suggestion, canViewAuthor))
                .createdAt(suggestion.getCreatedAt())
                .build();
    }

    private static String buildWriterDisplay(
            Suggestion suggestion,
            boolean canViewAuthor
    ) {
        if (!canViewAuthor) {
            return "익명";
        }
        User user = suggestion.getUser();
        if (user == null) {
            return "알 수 없음";
        }
        String department = user.getDepartment() != null ? user.getDepartment().getDescription() : "";
        String position = user.getPosition() != null ? user.getPosition().getDescription() : "";
        String combined = (department + " " + position).trim();
        return combined.isBlank() ? "알 수 없음" : combined;
    }

    private static boolean canViewAuthor(
            UserPrincipal viewer,
            Suggestion suggestion
    ) {
        if (!suggestion.isAnonymous()) {
            return true;
        }
        if (viewer == null) {
            return false;
        }
        return viewer.isAdmin();
    }

    private static Long extractUserId(Suggestion suggestion) {
        return suggestion.getUser() == null ? null : suggestion.getUser().getUserId();
    }
}