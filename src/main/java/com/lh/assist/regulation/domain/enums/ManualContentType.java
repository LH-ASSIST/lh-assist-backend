package com.lh.assist.regulation.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ManualContentType {
    ARTICLE_DESC("조항 설명"),
    ARTICLE_CLAUSE("조항 본문"),
    CHECKLIST_QUESTION("체크리스트 질문");

    private final String description;
}