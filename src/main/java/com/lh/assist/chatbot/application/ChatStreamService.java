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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletResponse;
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
    private static final String EVENT_ERROR = "error";
    private static final String ERROR_STREAMING_MESSAGE = "스트리밍 중 오류가 발생했습니다.";

    private record UpstreamStream(Flux<ServerSentEvent<String>> events, String streamId) {}

    private final WebClient chatbotWebClient;
    private final ObjectMapper objectMapper;
    private final AnalysisResultRepository analysisResultRepository;

    @Value("${app.chatbot.fastapi.stream-path:/ai/generate-stream}")
    private String streamPath;

    @Value("${app.chatbot.sse.timeout-ms:600000}")
    private long sseTimeoutMs;

    @Value("${app.chatbot.fastapi.connect-timeout-ms:15000}")
    private long upstreamConnectTimeoutMs;

    /**
     * FastAPI에서 SSE 스트림을 받아 클라이언트로 중계하고,
     * 스트림 완료 시 대화 내용을 chat_messages에 저장한다
     *
     * @param principal 로그인 사용자 ID (비로그인 사용자는 null)
     * @param request 대화 스트림 요청 정보
     * @return SSE 응답 emitter
     */
    public SseEmitter streamChat(
            UserPrincipal principal,
            @NonNull ChatStreamRequest request,
            String authorization
    ) {
        boolean hasAuth = authorization != null;
        validateStreamRequest(request, hasAuth);

        boolean hasSelection = request.hasSelection();
        boolean hasParsedJsonS3Key = request.hasParsedJsonS3Key();
        log.info(
                "chat_stream_validation auth={}, selection={}, parsedJsonS3KeyPresent={}",
                hasAuth,
                hasSelection,
                hasParsedJsonS3Key
        );

        log.debug("streamChat: principalId={}, isGuest={}, sessionId={}, questionLen={}, itemId={}, analysisResultId={}, analysisId={}, docId={}",
                principal == null ? null : principal.userId(),
                principal != null && principal.isGuest(),
                request.sessionId(),
                request.question() == null ? null : request.question().length(),
                request.itemId(),
                request.analysisResultId(),
                request.analysisId(),
                request.docId());

        long effectiveTimeoutMs = Math.max(sseTimeoutMs, 600_000L);
        log.info("chat_stream_timeout effectiveTimeoutMs={} (configured={})", effectiveTimeoutMs, sseTimeoutMs);
        SseEmitter emitter = new SseEmitter(effectiveTimeoutMs);
        StringBuilder answerBuffer = new StringBuilder();
        AtomicReference<List<RagReference>> ragReferences = new AtomicReference<>(null);
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>(null);
        AtomicBoolean finished = new AtomicBoolean(false);

        emitter.onCompletion(() -> cleanup(subscriptionRef, false, emitter));
        emitter.onTimeout(() -> cleanup(subscriptionRef, true, emitter));

        try {
            ChatStreamRequest enrichedRequest = enrichRequest(principal, request);
            UpstreamStream upstreamStream = createStream(enrichedRequest, authorization);
            applySseProxyHeaders(upstreamStream.streamId());
            Flux<ServerSentEvent<String>> stream = upstreamStream.events();
            Disposable subscription = stream.subscribe(
                    event -> handleStreamEvent(emitter, event, answerBuffer, ragReferences),
                    error -> handleStreamError(emitter, error, finished),
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
                emitter.complete();
            }
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
        if (data == null) {
            return;
        }

        String eventName = event.event();
        if (EVENT_REFERENCES.equals(eventName) || EVENT_REFS.equals(eventName)) {
            if (data.isBlank()) {
                return;
            }
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
            emitter.send(SseEmitter.event().name(EVENT_ERROR).data(ERROR_STREAMING_MESSAGE));
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
    private void validateStreamRequest(
            @NonNull ChatStreamRequest request,
            boolean hasAuth
    ) {
        if (request == null) {
            throw new ChatbotException(ErrorCode.INVALID_INPUT_VALUE);
        }
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
        if (request.analysisId() != null && request.analysisId() <= 0) {
            throw new ChatbotException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.docId() != null && request.docId() <= 0) {
            throw new ChatbotException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.itemId() != null && request.itemId() <= 0) {
            throw new ChatbotException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (hasAuth && request.hasSelection() && !request.hasParsedJsonS3Key()) {
            throw new ChatbotException(ErrorCode.DOCUMENT_CONTEXT_REQUIRED);
        }
    }

    private ChatStreamRequest enrichRequest(
            UserPrincipal principal,
            @NonNull ChatStreamRequest request
    ) {
        Long resolvedAnalysisId = request.resolvedAnalysisId();
        log.debug("enrichRequest: principalId={}, isGuest={}, analysisResultId={}, analysisId={}, reqDocId={}",
                principal == null ? null : principal.userId(),
                principal != null && principal.isGuest(),
                request.analysisResultId(),
                request.analysisId(),
                request.docId());

        if (resolvedAnalysisId == null) {
            if (principal == null || principal.isGuest()) {
                return withoutAnalysis(request);
            }
            return request;
        }
        if (principal == null || principal.isGuest()) {
            return withoutAnalysis(request);
        }

        AnalysisResult result = analysisResultRepository.findById(resolvedAnalysisId)
                .orElseThrow(() -> new ChatbotException(ErrorCode.DOCUMENT_CONTEXT_LOAD_FAILED));
        log.debug("analysisResult: id={}, status={}, documentId={}, ownerId={}, parsedJsonS3Key={}",
                result.getAnalysisId(),
                result.getStatus(),
                result.getDocument().getDocId(),
                result.getDocument().getUser().getUserId(),
                result.getParsedJsonS3Key());
        if (result.getStatus() != AnalysisResultStatus.SUCCEEDED) {
            throw new ChatbotException(ErrorCode.DOCUMENT_CONTEXT_LOAD_FAILED);
        }
        log.debug("ownerCheck: principalId={}, ownerId={}",
                principal.userId(),
                result.getDocument().getUser().getUserId());
        if (!Objects.equals(result.getDocument().getUser().getUserId(), principal.userId())) {
            throw new ChatbotException(ErrorCode.ACCESS_DENIED);
        }
        if (result.getParsedJsonS3Key() == null || result.getParsedJsonS3Key().isBlank()) {
            throw new ChatbotException(ErrorCode.DOCUMENT_CONTEXT_LOAD_FAILED);
        }

        return new ChatStreamRequest(
                request.sessionId(),
                request.question(),
                request.itemId(),
                request.analysisResultId(),
                result.getParsedJsonS3Key(),
                result.getDocument().getDocId(),
                result.getAnalysisId(),
                request.documentSelected(),
                request.analysisSelected(),
                request.useRag()
        );
    }

    private ChatStreamRequest withoutAnalysis(@NonNull ChatStreamRequest request) {
        return new ChatStreamRequest(
                request.sessionId(),
                request.question(),
                request.itemId(),
                null,
                null,
                null,
                null,
                null,
                null,
                request.useRag()
        );
    }

    /**
     * FastAPI SSE 스트림을 생성한다
     *
     * @param request 스트림 요청 정보
     * @return SSE 스트림
     */
    private void applySseProxyHeaders(String upstreamStreamId) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletResponse response = attributes.getResponse();
        if (response == null) {
            return;
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader(HttpHeaders.CONNECTION, "keep-alive");
        String exposedStreamId = (upstreamStreamId == null || upstreamStreamId.isBlank())
                ? UUID.randomUUID().toString()
                : upstreamStreamId;
        response.setHeader("X-Stream-Id", exposedStreamId);
    }

    private UpstreamStream createStream(
            @NonNull ChatStreamRequest request,
            String authorization
    ) {
        log.debug("createStream request: {}", request);
        WebClient.RequestBodySpec spec = chatbotWebClient.post()
                .uri(streamPath)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM);
        if (authorization != null && !authorization.isBlank()) {
            spec = spec.header("Authorization", authorization);
        }
        Flux<ServerSentEvent<String>> body = spec.bodyValue(request)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .doOnNext(event -> log.debug(
                        "RAW SSE EVENT: name={}, dataLength={}",
                        event.event(),
                        event.data() == null ? null : event.data().length()
                ));
        return new UpstreamStream(body, null);
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
        String eventName = event.event();
        String data = event.data();
        log.debug("SSE event received: name={}, dataLength={}", eventName, data == null ? null : data.length());
        if (EVENT_DONE.equals(eventName) || "[DONE]".equals(data)) {
            try {
                emitter.send(SseEmitter.event().name(EVENT_DONE).data("[DONE]"));
            } catch (IOException ex) {
                emitter.complete();
                return;
            }
            emitter.complete();
            return;
        }
        if (EVENT_ERROR.equals(eventName)) {
            try {
                String payload = data == null ? ERROR_STREAMING_MESSAGE : data;
                emitter.send(SseEmitter.event().name(EVENT_ERROR).data(payload));
            } catch (IOException ex) {
                emitter.complete();
            }
            emitter.complete();
            return;
        }
        handleEvent(event, answerBuffer, ragReferences);
        try {
            if (data == null) {
                return;
            }
            SseEmitter.SseEventBuilder builder = SseEmitter.event();
            if (eventName == null || eventName.isBlank()) {
                eventName = "message";
            }
            builder.name(eventName);
            builder.data(data);
            emitter.send(builder);
        } catch (IOException ex) {
            emitter.complete();
        }
    }

    /**
     * SSE 스트리밍 오류를 처리한다
     *
     * @param emitter SSE emitter
     * @param error 오류
     * @param finished 스트리밍 완료 여부
     */
    private void handleStreamError(
            @NonNull SseEmitter emitter,
            @NonNull Throwable error,
            @NonNull AtomicBoolean finished
    ) {
        if (finished.compareAndSet(false, true)) {
            log.warn("챗봇 스트리밍 중 오류 발생: {}", error.getMessage(), error);
            sendErrorEvent(emitter);
            emitter.complete();
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
     * 구독을 정리한다
     *
     * @param subscriptionRef 스트림 구독 참조
     * @param completeEmitter emitter 완료 여부
     * @param emitter SSE emitter
     */
    private void cleanup(
            @NonNull AtomicReference<Disposable> subscriptionRef,
            boolean completeEmitter,
            @NonNull SseEmitter emitter
    ) {
        Disposable subscription = subscriptionRef.get();
        if (subscription != null) {
            subscription.dispose();
        }
        if (completeEmitter) {
            emitter.complete();
        }
    }
}
