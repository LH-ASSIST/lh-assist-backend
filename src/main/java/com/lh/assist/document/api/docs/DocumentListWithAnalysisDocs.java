package com.lh.assist.document.api.docs;

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
        summary = "문서 목록 + 분석 요약 조회",
        description = "사용자 문서 목록과 최신 분석 요약 정보를 함께 조회합니다."
)
@ApiResponse(responseCode = "200", description = "조회 성공",
        content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "500", description = "서버 오류",
        content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
public @interface DocumentListWithAnalysisDocs {
}