package com.lh.assist.analysis.api.docs;

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
        summary = "분석 완료 콜백",
        description = "분석 서버에서 전달된 콜백을 처리합니다."
)
@ApiResponse(responseCode = "200", description = "처리 성공",
        content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "400", description = "입력값 오류",
        content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "401", description = "콜백 토큰 불일치",
        content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "404", description = "분석 작업/결과 없음",
        content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "500", description = "서버 오류",
        content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
public @interface AnalysisCallbackDocs {
}
