package com.lh.assist.chatbot.api.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "챗봇 SSE 스트리밍",
        description = "FastAPI 응답을 SSE로 중계하여 실시간 답변을 전송합니다. 비로그인도 사용 가능하지만 분석 결과 컨텍스트는 사용되지 않습니다. 로그인 사용자가 문서/분석을 선택한 요청(documentSelected/analysisSelected/docId/analysisId/itemId)에는 parsedJsonS3Key가 필수입니다. SSE 이벤트는 message(답변 텍스트), references/refs(근거 JSON), ping(하트비트), done(종료) 순으로 전달됩니다."
)
@ApiResponse(responseCode = "200", description = "스트리밍 시작",
        content = @Content(mediaType = "text/event-stream"))
@ApiResponse(responseCode = "400", description = "입력값 오류",
        content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "429", description = "요청 과다",
        content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "500", description = "서버 오류",
        content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
public @interface ChatStreamDocs {
}