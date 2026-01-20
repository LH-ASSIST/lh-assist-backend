package com.lh.assist.common.exception;

import lombok.Builder;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Builder
@Schema(description = "에러 응답")
public class ErrorResponse {
    @Schema(description = "HTTP 상태 코드", example = "400")
    private final int status;
    @Schema(description = "에러 코드", example = "C001")
    private final String code;
    @Schema(description = "에러 메시지", example = "입력값이 올바르지 않습니다.")
    private final String message;
}
