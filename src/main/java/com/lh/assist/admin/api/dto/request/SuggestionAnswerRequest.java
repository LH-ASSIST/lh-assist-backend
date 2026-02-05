package com.lh.assist.admin.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SuggestionAnswerRequest(
        @NotBlank
        String answerContent
) {
}