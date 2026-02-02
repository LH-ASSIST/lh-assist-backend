package com.lh.assist.chatbot.api;

import com.lh.assist.chatbot.api.docs.ChatSessionCreateDocs;
import com.lh.assist.chatbot.api.dto.response.ChatSessionResponse;
import com.lh.assist.chatbot.api.mapper.ChatSessionMapper;
import com.lh.assist.chatbot.application.ChatSessionService;
import com.lh.assist.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @PostMapping("/sessions")
    @ChatSessionCreateDocs
    public ResponseEntity<ApiResponse<ChatSessionResponse>> createSession() {
        String sessionId = chatSessionService.createSessionId();
        ChatSessionResponse response = ChatSessionMapper.toResponse(sessionId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }
}