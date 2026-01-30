package com.lh.assist.notice.api.dto.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NoticeSearchType {
    ALL("제목+내용"),
    TITLE("제목"),
    CONTENT("내용");

    private final String description;
}