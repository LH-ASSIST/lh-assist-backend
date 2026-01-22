package com.lh.assist.suggestion.api.dto;

import com.lh.assist.suggestion.domain.SuggestionCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SuggestionCreateRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    private String content;

    @NotNull
    private SuggestionCategory category;

    private boolean isPrivate;

    private boolean isAnonymous;
}