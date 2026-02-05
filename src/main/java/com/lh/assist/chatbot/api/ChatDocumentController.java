package com.lh.assist.chatbot.api;

import com.lh.assist.chatbot.api.docs.ChatbotApiDocs;
import com.lh.assist.chatbot.api.docs.ChatDocumentDocs;
import com.lh.assist.chatbot.api.dto.response.ChatDocumentResponse;
import com.lh.assist.chatbot.application.ChatDocumentService;
import com.lh.assist.common.model.ApiResponse;
import com.lh.assist.common.security.UserPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat")
@ChatbotApiDocs
public class ChatDocumentController {

    private final ChatDocumentService chatDocumentService;

    @GetMapping("/documents")
    @PreAuthorize("isAuthenticated()")
    @ChatDocumentDocs
    public ResponseEntity<ApiResponse<List<ChatDocumentResponse>>> getSelectableDocuments(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<ChatDocumentResponse> responses =
                chatDocumentService.getSelectableDocuments(principal.userId());
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}