package com.lh.assist.admin.suggestion.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SuggestionAnswerRequest(
        @NotBlank
        String answerContent
) {
}