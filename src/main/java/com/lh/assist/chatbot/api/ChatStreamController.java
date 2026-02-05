package com.lh.assist.chatbot.api;

import com.lh.assist.chatbot.api.docs.ChatStreamDocs;
import com.lh.assist.chatbot.api.docs.ChatbotApiDocs;
import com.lh.assist.chatbot.api.dto.request.ChatStreamRequest;
import com.lh.assist.chatbot.application.ChatStreamService;
import com.lh.assist.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat")
@ChatbotApiDocs
public class ChatStreamController {

    private final ChatStreamService chatStreamService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ChatStreamDocs
    public SseEmitter streamChat(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String sessionId,
            @RequestParam String question,
            @RequestParam(required = false) Long itemId
    ) {
        ChatStreamRequest request = new ChatStreamRequest(sessionId, question, itemId);
        Long userId = principal != null ? principal.userId() : null;
        return chatStreamService.streamChat(userId, request);
    }
}