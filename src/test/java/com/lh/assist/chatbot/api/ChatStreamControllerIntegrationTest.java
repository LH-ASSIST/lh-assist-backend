package com.lh.assist.chatbot.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lh.assist.chatbot.api.dto.request.ChatStreamRequest;
import com.lh.assist.chatbot.application.ChatStreamService;
import com.lh.assist.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@AutoConfigureMockMvc
class ChatStreamControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatStreamService chatStreamService;

    @TestConfiguration
    static class ChatStreamControllerTestConfig {
        @Bean
        ChatStreamService chatStreamService() {
            return Mockito.mock(ChatStreamService.class);
        }
    }

    @Test
    @DisplayName("요청 파라미터가 없으면 500이 반환되어야 한다")
    void missing_params_returns_400() throws Exception {
        RequestPostProcessor clientIp = request -> {
            request.setRemoteAddr("203.0.113.1");
            return request;
        };

        mockMvc.perform(get("/api/v1/chat/stream").with(clientIp))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("정상 요청이면 SSE 응답을 반환하고 파라미터를 전달해야 한다")
    void stream_request_delegates_to_service() throws Exception {
        when(chatStreamService.streamChat(isNull(), any(ChatStreamRequest.class)))
                .thenReturn(new SseEmitter(1000L));

        RequestPostProcessor clientIp = request -> {
            request.setRemoteAddr("203.0.113.2");
            return request;
        };

        mockMvc.perform(get("/api/v1/chat/stream")
                        .with(clientIp)
                        .param("sessionId", "session-1")
                        .param("question", "질문")
                        .param("itemId", "123")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk());

        ArgumentCaptor<ChatStreamRequest> requestCaptor = ArgumentCaptor.forClass(ChatStreamRequest.class);
        verify(chatStreamService).streamChat(isNull(), requestCaptor.capture());
        ChatStreamRequest captured = requestCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(captured.sessionId()).isEqualTo("session-1");
        org.assertj.core.api.Assertions.assertThat(captured.question()).isEqualTo("질문");
        org.assertj.core.api.Assertions.assertThat(captured.itemId()).isEqualTo(123L);
    }

    @Test
    @DisplayName("분당 호출 제한을 초과하면 429가 반환되어야 한다")
    void rate_limit_exceeded_returns_429() throws Exception {
        when(chatStreamService.streamChat(isNull(), any(ChatStreamRequest.class)))
                .thenReturn(new SseEmitter(1000L));

        RequestPostProcessor clientIp = request -> {
            request.setRemoteAddr("203.0.113.10");
            return request;
        };

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/v1/chat/stream")
                            .with(clientIp)
                            .param("sessionId", "session-1")
                            .param("question", "질문")
                            .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(request().asyncStarted())
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/chat/stream")
                        .with(clientIp)
                        .param("sessionId", "session-1")
                        .param("question", "질문")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isTooManyRequests());
    }
}
