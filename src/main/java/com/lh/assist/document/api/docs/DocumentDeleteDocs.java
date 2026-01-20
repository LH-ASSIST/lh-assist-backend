package com.lh.assist.document.api.docs;

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
@Operation(summary = "문서 삭제", description = "본인 문서를 삭제합니다.")
@ApiResponses({
		@ApiResponse(responseCode = "204", description = "삭제 성공",
				content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class))),
		@ApiResponse(responseCode = "400", description = "입력값 오류",
				content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class))),
		@ApiResponse(responseCode = "401", description = "인증 필요",
				content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class))),
		@ApiResponse(responseCode = "403", description = "권한 없음",
				content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class))),
		@ApiResponse(responseCode = "404", description = "문서 없음",
				content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류",
				content = @Content(schema = @Schema(implementation = com.lh.assist.common.model.ApiResponse.class)))
})
public @interface DocumentDeleteDocs {
}