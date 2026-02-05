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
        summary = "챗봇 세션 생성",
        description = "챗봇 대화를 위한 세션 ID를 생성합니다. 비로그인도 사용 가능합니다. 세션은 서버에 저장되지 않으며 종료 API도 제공되지 않습니다."
)
@ApiResponse(responseCode = "201", description = "세션 생성 완료",
        content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "500", description = "서버 오류",
        content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
public @interface ChatSessionCreateDocs {
}