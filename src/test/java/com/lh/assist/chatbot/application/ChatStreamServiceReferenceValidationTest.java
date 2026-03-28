package com.lh.assist.chatbot.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.analysis.domain.repository.AnalysisResultRepository;
import com.lh.assist.chatbot.domain.enums.RagReferenceType;
import com.lh.assist.chatbot.domain.model.RagReference;
import com.lh.assist.common.exception.ChatbotException;
import com.lh.assist.common.exception.ErrorCode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
class ChatStreamServiceReferenceValidationTest {

    @Mock
    private WebClient webClient;

    @Mock
    private AnalysisResultRepository analysisResultRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("RAG 근거에 type이 없으면 INVALID_INPUT_VALUE가 발생해야 한다")
    void 근거_타입_누락() throws Exception {
        ChatStreamService service = new ChatStreamService(
                webClient,
                objectMapper,
                analysisResultRepository
        );
        List<RagReference> references = List.of(new RagReference(null, 1L, "제목", 0.5, null, null, null));

        invokeValidateReferences(service, references);
    }

    @Test
    @DisplayName("RAG 근거에 id가 없으면 INVALID_INPUT_VALUE가 발생해야 한다")
    void 근거_ID_누락() throws Exception {
        ChatStreamService service = new ChatStreamService(
                webClient,
                objectMapper,
                analysisResultRepository
        );
        List<RagReference> references = List.of(new RagReference(RagReferenceType.REGULATION, null, "제목", 0.5, null, null, null));

        invokeValidateReferences(service, references);
    }

    @Test
    @DisplayName("RAG 근거 score가 0~1 범위를 벗어나면 INVALID_INPUT_VALUE가 발생해야 한다")
    void 근거_SCORE_범위_초과() throws Exception {
        ChatStreamService service = new ChatStreamService(
                webClient,
                objectMapper,
                analysisResultRepository
        );
        List<RagReference> references = List.of(new RagReference(RagReferenceType.REGULATION, 1L, "제목", 1.5, null, null, null));

        invokeValidateReferences(service, references);
    }

    private void invokeValidateReferences(
            ChatStreamService service,
            List<RagReference> references
    ) throws Exception {
        Method method = ChatStreamService.class.getDeclaredMethod("validateReferences", List.class);
        method.setAccessible(true);

        assertThatThrownBy(() -> method.invoke(service, references))
                .isInstanceOf(InvocationTargetException.class)
                .extracting(Throwable::getCause)
                .isInstanceOf(ChatbotException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
