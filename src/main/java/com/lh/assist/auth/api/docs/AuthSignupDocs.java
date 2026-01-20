package com.lh.assist.auth.api.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(summary = "회원가입", description = "사용자 계정을 생성합니다.")
@ApiResponses({
		@ApiResponse(responseCode = "201", description = "생성 성공",
				content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class))),
		@ApiResponse(responseCode = "400", description = "입력값 오류",
				content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class))),
		@ApiResponse(responseCode = "409", description = "이메일 중복",
				content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류",
				content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
})
public @interface AuthSignupDocs {
}
