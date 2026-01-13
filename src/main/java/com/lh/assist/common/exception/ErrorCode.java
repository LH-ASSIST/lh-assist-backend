package com.lh.assist.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부 오류가 발생했습니다."),

    AI_ANALYSIS_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "A001", "AI 분석 서버와 통신에 실패했습니다."),
    DOCUMENT_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "A002", "지원하지 않는 문서 형식입니다."),

    REGULATION_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "해당 기준일의 유효한 규정을 찾을 수 없습니다."),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "S001", "권한이 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "S002", "인증 정보가 유효하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(
            HttpStatus status,
            String code,
            String message
    ) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}