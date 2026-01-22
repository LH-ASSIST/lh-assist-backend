package com.lh.assist.document.api.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(summary = "문서 업로드", description = "문서 파일을 업로드하고 Document를 생성합니다.")
@ApiResponse(responseCode = "201", description = "생성 성공",
		content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "400", description = "파일 형식/필수 값 누락",
		content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "401", description = "인증 필요",
		content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "413", description = "파일 크기 초과",
		content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@ApiResponse(responseCode = "500", description = "S3 업로드 실패/서버 오류",
		content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
@Parameters({})
public @interface DocumentUploadDocs {
}
