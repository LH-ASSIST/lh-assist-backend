package com.lh.assist.chatbot.api.dto.request;

public record ChatStreamRequest(
        String sessionId,
        String question,
        Long itemId
) {
}