package com.lh.assist.chatbot.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 3개의 RAG 소스 전체를 담는 스냅샷
// 한 메세지에 여러 타입이 섞여서 저장 가능
@Getter
@RequiredArgsConstructor
public enum RagReferenceType {
    REGULATION("법령/규정"),
    CASE("사례/감사결과"),
    MANUAL("메뉴얼/가이드라인");

    private final String description;
}