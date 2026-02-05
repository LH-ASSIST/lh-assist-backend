package com.lh.assist.chatbot.api.dto.request;

public record ChatStreamRequest(
        String sessionId,
        String question,
        Long itemId,
        Long analysisResultId,
        String parsedJsonS3Key,
        Long docId
) {
}