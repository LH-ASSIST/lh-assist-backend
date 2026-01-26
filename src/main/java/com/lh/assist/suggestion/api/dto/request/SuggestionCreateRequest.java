package com.lh.assist.suggestion.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lh.assist.suggestion.domain.enums.SuggestionCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SuggestionCreateRequest(
        @NotBlank
        @Size(max = 200)
        String title,
        @NotBlank
        String content,
        @NotNull
        SuggestionCategory category,
        @JsonProperty("isPrivate")
        boolean isPrivate,
        @JsonProperty("isAnonymous")
        boolean isAnonymous
) {
}