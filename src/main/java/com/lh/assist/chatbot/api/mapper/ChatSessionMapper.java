package com.lh.assist.chatbot.api.mapper;

import com.lh.assist.chatbot.api.dto.response.ChatSessionResponse;

public final class ChatSessionMapper {

    private ChatSessionMapper() {
    }

    public static ChatSessionResponse toResponse(String sessionId) {
        return new ChatSessionResponse(sessionId);
    }
}