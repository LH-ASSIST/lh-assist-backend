package com.lh.assist.chatbot.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.analysis.domain.repository.AnalysisResultRepository;
import com.lh.assist.chatbot.domain.enums.RagReferenceType;
import com.lh.assist.chatbot.domain.model.RagReference;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
class ChatStreamServiceEventParsingTest {

    @Mock
    private WebClient webClient;

    @Mock
    private AnalysisResultRepository analysisResultRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ChatStreamService service;

    @BeforeEach
    void setUp() {
        service = new ChatStreamService(
                webClient,
                objectMapper,
                analysisResultRepository
        );
    }

    @Test
    @DisplayName("references 이벤트는 RAG 근거를 파싱해 저장해야 한다")
    void references_event_parses_references() throws Exception {
        List<RagReference> refs = List.of(
                new RagReference(RagReferenceType.REGULATION, 1L, "제목", 0.5, null, null, 0)
        );
        String json = objectMapper.writeValueAsString(refs);
        ServerSentEvent<String> event = ServerSentEvent.builder(json)
                .event("references")
                .build();
        StringBuilder buffer = new StringBuilder();
        AtomicReference<List<RagReference>> ragReferences = new AtomicReference<>(null);

        invokeHandleEvent(event, buffer, ragReferences);

        assertThat(buffer).isEmpty();
        assertThat(ragReferences.get()).hasSize(1);
        assertThat(ragReferences.get().get(0).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("done 이벤트는 답변에 포함되지 않아야 한다")
    void done_event_does_not_append() throws Exception {
        ServerSentEvent<String> event = ServerSentEvent.builder("ignored")
                .event("done")
                .build();
        StringBuilder buffer = new StringBuilder();
        AtomicReference<List<RagReference>> ragReferences = new AtomicReference<>(null);

        invokeHandleEvent(event, buffer, ragReferences);

        assertThat(buffer).isEmpty();
        assertThat(ragReferences.get()).isNull();
    }

    @Test
    @DisplayName("일반 이벤트는 답변 버퍼에 누적되어야 한다")
    void normal_event_appends_to_buffer() throws Exception {
        ServerSentEvent<String> event = ServerSentEvent.builder("hello").build();
        StringBuilder buffer = new StringBuilder();
        AtomicReference<List<RagReference>> ragReferences = new AtomicReference<>(null);

        invokeHandleEvent(event, buffer, ragReferences);

        assertThat(buffer).hasToString("hello");
        assertThat(ragReferences.get()).isNull();
    }

    private void invokeHandleEvent(
            ServerSentEvent<String> event,
            StringBuilder buffer,
            AtomicReference<List<RagReference>> ragReferences
    ) throws Exception {
        Method method = ChatStreamService.class.getDeclaredMethod(
                "handleEvent",
                ServerSentEvent.class,
                StringBuilder.class,
                AtomicReference.class
        );
        method.setAccessible(true);
        method.invoke(service, event, buffer, ragReferences);
    }
}
