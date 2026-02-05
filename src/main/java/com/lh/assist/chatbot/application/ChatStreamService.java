package com.lh.assist.chatbot.application;

import com.lh.assist.chatbot.api.dto.request.ChatStreamRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lh.assist.chatbot.domain.model.RagReference;
import com.lh.assist.common.exception.ChatbotException;
import com.lh.assist.common.exception.ErrorCode;
import com.lh.assist.analysis.domain.entity.AnalysisResult;
import com.lh.assist.analysis.domain.enums.AnalysisResultStatus;
import com.lh.assist.analysis.domain.repository.AnalysisResultRepository;
import com.lh.assist.common.security.UserPrincipal;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatStreamService {

    private static final String EVENT_REFERENCES = "references";
    private static final String EVENT_REFS = "refs";
    private static final String EVENT_DONE = "done";
    private static final String EVENT_PING = "ping";

    private final WebClient chatbotWebClient;
    private final ObjectMapper objectMapper;
    private final TaskScheduler chatStreamTaskScheduler;
    private final AnalysisResultRepository analysisResultRepository;

    @Value("${app.chatbot.fastapi.stream-path:/generate-stream}")
    private String streamPath;

    @Value("${app.chatbot.sse.timeout-ms:60000}")
    private long sseTimeoutMs;

    @Value("${app.chatbot.sse.heartbeat-ms:15000}")
    private long heartbeatMs;

    /**
     * FastAPI에서 SSE 스트림을 받아 클라이언트로 중계하고,
     * 스트림 완료 시 대화 내용을 chat_messages에 저장한다
     *
     * @param userId 로그인 사용자 ID (비로그인 사용자는 null)
     * @param request 대화 스트림 요청 정보
     * @return SSE 응답 emitter
     */
    public SseEmitter streamChat(
            UserPrincipal principal,
            @NonNull ChatStreamRequest request
    ) {
        validateStreamRequest(request);

        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        StringBuilder answerBuffer = new StringBuilder();
        AtomicReference<List<RagReference>> ragReferences = new AtomicReference<>(null);
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>(null);
        AtomicBoolean finished = new AtomicBoolean(false);
        ScheduledFuture<?> heartbeatFuture = startHeartbeat(emitter, finished);

        emitter.onCompletion(() -> cleanup(subscriptionRef, heartbeatFuture, false, emitter));
        emitter.onTimeout(() -> cleanup(subscriptionRef, heartbeatFuture, true, emitter));

        try {
            ChatStreamRequest enrichedRequest = enrichRequest(principal, request);
            Flux<ServerSentEvent<String>> stream = createStream(enrichedRequest);
            Disposable subscription = stream.subscribe(
                    event -> handleStreamEvent(emitter, event, answerBuffer, ragReferences),
                    error -> handleStreamError(emitter, error, finished, heartbeatFuture),
                    () -> handleStreamComplete(
                            emitter,
                            principal != null ? principal.userId() : null,
                            request,
                            answerBuffer,
                            ragReferences,
                            finished
                    )
            );
            subscriptionRef.set(subscription);
        } catch (RuntimeException ex) {
            if (finished.compareAndSet(false, true)) {
                log.warn("챗봇 스트리밍 초기화 중 오류 발생: {}", ex.getMessage(), ex);
                sendErrorEvent(emitter);
                emitter.completeWithError(ex);
            }
            heartbeatFuture.cancel(true);
            return emitter;
        }

        return emitter;
    }

    /**
     * FastAPI SSE 이벤트를 해석하여 답변 텍스트와 RAG 근거(JSON)를 분리 저장한다
     *
     * @param event 수신한 SSE 이벤트
     * @param answerBuffer 누적 답변 버퍼
     * @param ragReferences RAG 근거 객체 리스트
     */
    private void handleEvent(
            @NonNull ServerSentEvent<String> event,
            @NonNull StringBuilder answerBuffer,
            @NonNull AtomicReference<List<RagReference>> ragReferences
    ) {
        String data = event.data();
        if (data == null || data.isBlank()) {
            return;
        }

        String eventName = event.event();
        if (EVENT_REFERENCES.equals(eventName) || EVENT_REFS.equals(eventName)) {
            ragReferences.set(parseReferencesJson(data));
            return;
        }
        if (EVENT_DONE.equals(eventName)) {
            return;
        }

        answerBuffer.append(data);
    }

    /**
     * 스트리밍이 끝난 뒤 대화 내용을 DB에 저장한다
     *
     * 현재는 저장을 비활성화한 상태
     */
    private void persistChatMessage(
            Long userId,
            @NonNull ChatStreamRequest request,
            @NonNull String answer,
            List<RagReference> ragReferences
    ) {
        log.debug(
                "채팅 저장 비활성화: userId={}, sessionId={}, answerLength={}, ragReferenceCount={}",
                userId,
                request.sessionId(),
                answer.length(),
                ragReferences == null ? 0 : ragReferences.size()
        );
    }

    /**
     * SSE 에러 이벤트를 전송한다
     *
     * @param emitter SSE emitter
     */
    private void sendErrorEvent(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("error").data("스트리밍 중 오류가 발생했습니다."));
        } catch (IOException ex) {
            log.warn("에러 이벤트 전송 실패: {}", ex.getMessage(), ex);
        }
    }

    /**
     * RAG 근거 JSON 문자열을 객체 리스트로 파싱한다
     *
     * @param json RAG 근거 JSON 문자열
     * @return 파싱된 근거 리스트
     */
    private List<RagReference> parseReferencesJson(@NonNull String json) {
        try {
            List<RagReference> references = objectMapper.readValue(
                    json,
                    new TypeReference<>() {
                    }
            );
            validateReferences(references);
            return references;
        } catch (IOException ex) {
            throw new ChatbotException(ErrorCode.INVALID_INPUT_VALUE, ex);
        }
    }

    /**
     * RAG 근거 리스트 스키마를 검증한다
     *
     * @param references RAG 근거 리스트
     */
    private void validateReferences(@NonNull List<RagReference> references) {
        for (RagReference reference : references) {
            if (reference == null) {
                throw new ChatbotException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (reference.type() == null) {
                throw new ChatbotException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (reference.id() == null) {
                throw new ChatbotException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (reference.score() != null) {
                double score = reference.score();
                if (score < 0.0 || score > 1.0) {
                    throw new ChatbotException(ErrorCode.INVALID_INPUT_VALUE);
                }
            }
        }
    }

    /**
     * SSE 요청 입력값을 검증한다
     *
     * @param request 스트림 요청 정보
     */
    private void validateStreamRequest(@NonNull ChatStreamRequest request) {
        if (request.sessionId() == null || request.sessionId().isBlank()) {
            throw new ChatbotException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.question() == null || request.question().isBlank()) {
            throw new ChatbotException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.question().length() > 500) {
            throw new ChatbotException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.analysisResultId() != null && request.analysisResultId() <= 0) {
            throw new ChatbotException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private ChatStreamRequest enrichRequest(
            UserPrincipal principal,
            @NonNull ChatStreamRequest request
    ) {
        if (request.analysisResultId() == null) {
            if (principal == null || principal.isGuest()) {
                return withoutAnalysis(request);
            }
            return request;
        }
        if (principal == null || principal.isGuest()) {
            return withoutAnalysis(request);
        }

        AnalysisResult result = analysisResultRepository.findById(request.analysisResultId())
                .orElseThrow(() -> new ChatbotException(ErrorCode.INVALID_INPUT_VALUE));
        if (result.getStatus() != AnalysisResultStatus.SUCCEEDED) {
            throw new ChatbotException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!Objects.equals(result.getDocument().getUser().getUserId(), principal.userId())) {
            throw new ChatbotException(ErrorCode.ACCESS_DENIED);
        }

        return new ChatStreamRequest(
                request.sessionId(),
                request.question(),
                request.itemId(),
                request.analysisResultId(),
                result.getParsedJsonS3Key(),
                result.getDocument().getDocId()
        );
    }

    private ChatStreamRequest withoutAnalysis(@NonNull ChatStreamRequest request) {
        return new ChatStreamRequest(
                request.sessionId(),
                request.question(),
                request.itemId(),
                null,
                null,
                null
        );
    }

    /**
     * SSE 하트비트를 주기적으로 전송한다
     *
     * @param emitter SSE emitter
     * @param finished 스트리밍 완료 여부
     * @return 하트비트 스케줄러
     */
    private ScheduledFuture<?> startHeartbeat(
            @NonNull SseEmitter emitter,
            @NonNull AtomicBoolean finished
    ) {
        return chatStreamTaskScheduler.scheduleAtFixedRate(() -> {
            if (finished.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(EVENT_PING).data("heartbeat"));
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        }, Duration.ofMillis(heartbeatMs));
    }

    /**
     * FastAPI SSE 스트림을 생성한다
     *
     * @param request 스트림 요청 정보
     * @return SSE 스트림
     */
    private Flux<ServerSentEvent<String>> createStream(@NonNull ChatStreamRequest request) {
        return chatbotWebClient.post()
                .uri(streamPath)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<>() {
                });
    }

    /**
     * 수신한 SSE 이벤트를 처리하고 클라이언트로 전달한다
     *
     * @param emitter SSE emitter
     * @param event 수신 이벤트
     * @param answerBuffer 누적 답변 버퍼
     * @param ragReferences RAG 근거 객체 리스트
     */
    private void handleStreamEvent(
            @NonNull SseEmitter emitter,
            @NonNull ServerSentEvent<String> event,
            @NonNull StringBuilder answerBuffer,
            @NonNull AtomicReference<List<RagReference>> ragReferences
    ) {
        handleEvent(event, answerBuffer, ragReferences);
        try {
            if (event.data() == null) {
                return;
            }
            SseEmitter.SseEventBuilder builder = SseEmitter.event();
            String eventName = event.event();
            if (eventName != null) {
                builder.name(eventName);
            }
            builder.data(event.data());
            emitter.send(builder);
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    /**
     * SSE 스트리밍 오류를 처리한다
     *
     * @param emitter SSE emitter
     * @param error 오류
     * @param finished 스트리밍 완료 여부
     * @param heartbeatFuture 하트비트
     */
    private void handleStreamError(
            @NonNull SseEmitter emitter,
            @NonNull Throwable error,
            @NonNull AtomicBoolean finished,
            @NonNull ScheduledFuture<?> heartbeatFuture
    ) {
        if (finished.compareAndSet(false, true)) {
            log.warn("챗봇 스트리밍 중 오류 발생: {}", error.getMessage(), error);
            sendErrorEvent(emitter);
            emitter.completeWithError(error);
            heartbeatFuture.cancel(true);
        }
    }

    /**
     * SSE 스트리밍 완료 처리를 수행한다
     *
     * @param emitter SSE emitter
     * @param userId 로그인 사용자 ID
     * @param request 스트림 요청 정보
     * @param answerBuffer 누적 답변 버퍼
     * @param ragReferences RAG 근거 객체 리스트
     * @param finished 스트리밍 완료 여부
     */
    private void handleStreamComplete(
            @NonNull SseEmitter emitter,
            Long userId,
            @NonNull ChatStreamRequest request,
            @NonNull StringBuilder answerBuffer,
            @NonNull AtomicReference<List<RagReference>> ragReferences,
            @NonNull AtomicBoolean finished
    ) {
        if (finished.compareAndSet(false, true)) {
            persistChatMessage(userId, request, answerBuffer.toString(), ragReferences.get());
            emitter.complete();
        }
    }

    /**
     * 구독과 하트비트 스케줄러를 정리한다
     *
     * @param subscriptionRef 스트림 구독 참조
     * @param heartbeatFuture 하트비트
     * @param completeEmitter emitter 완료 여부
     * @param emitter SSE emitter
     */
    private void cleanup(
            @NonNull AtomicReference<Disposable> subscriptionRef,
            @NonNull ScheduledFuture<?> heartbeatFuture,
            boolean completeEmitter,
            @NonNull SseEmitter emitter
    ) {
        Disposable subscription = subscriptionRef.get();
        if (subscription != null) {
            subscription.dispose();
        }
        heartbeatFuture.cancel(true);
        if (completeEmitter) {
            emitter.complete();
        }
    }
}
