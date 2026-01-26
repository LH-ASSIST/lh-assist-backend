package com.lh.assist.auth.api.docs;

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
@Operation(summary = "로그아웃", description = "리프레시 토큰을 폐기하고 액세스 토큰을 블랙리스트 처리합니다.")
@ApiResponse(responseCode = "200", description = "로그아웃 성공",
		content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "400", description = "입력값 오류",
		content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "401", description = "인증 실패",
		content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "500", description = "서버 오류",
		content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
public @interface AuthLogoutDocs {
}