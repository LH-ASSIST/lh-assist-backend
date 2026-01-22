package com.lh.assist.suggestion.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuggestionCategory {
    SYSTEM_ERROR("시스템 오류"),
    RECOMMENDATION("기능 추천"),
    ETC("기타");

    private final String description;
}