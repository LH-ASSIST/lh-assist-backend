package com.lh.assist.suggestion.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuggestionStatus {
    WAITING("답변 대기"),
    ANSWERED("답변 완료");

    private final String description;
}