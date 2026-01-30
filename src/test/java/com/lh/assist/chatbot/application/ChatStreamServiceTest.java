package com.lh.assist.chatbot.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.chatbot.api.dto.request.ChatStreamRequest;
import com.lh.assist.common.exception.ChatbotException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.support.ReflectionTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
class ChatStreamServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private TaskScheduler taskScheduler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("질문이 500자를 초과하면 INVALID_INPUT_VALUE가 발생해야 한다")
    void 질문_길이_초과() {
        ChatStreamService service = new ChatStreamService(webClient, objectMapper, taskScheduler);
        ReflectionTestUtils.setField(service, "streamPath", "/generate-stream");
        ReflectionTestUtils.setField(service, "sseTimeoutMs", 1000L);

        String question = "a".repeat(501);
        ChatStreamRequest request = new ChatStreamRequest("session-1", question, null);

        assertThatThrownBy(() -> service.streamChat(null, request))
                .isInstanceOf(ChatbotException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("요청이 null이면 INVALID_INPUT_VALUE가 발생해야 한다")
    void 요청_널() {
        ChatStreamService service = new ChatStreamService(webClient, objectMapper, taskScheduler);
        ReflectionTestUtils.setField(service, "streamPath", "/generate-stream");
        ReflectionTestUtils.setField(service, "sseTimeoutMs", 1000L);

        assertThatThrownBy(() -> service.streamChat(null, null))
                .isInstanceOf(ChatbotException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("세션 아이디가 비어있으면 INVALID_INPUT_VALUE가 발생해야 한다")
    void 세션_아이디_누락() {
        ChatStreamService service = new ChatStreamService(webClient, objectMapper, taskScheduler);
        ReflectionTestUtils.setField(service, "streamPath", "/generate-stream");
        ReflectionTestUtils.setField(service, "sseTimeoutMs", 1000L);

        ChatStreamRequest request = new ChatStreamRequest(" ", "질문", null);

        assertThatThrownBy(() -> service.streamChat(null, request))
                .isInstanceOf(ChatbotException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}