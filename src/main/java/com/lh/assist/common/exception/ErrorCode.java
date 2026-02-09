package com.lh.assist.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "C003", "파일 크기가 허용 한도를 초과했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부 오류가 발생했습니다."),

    AI_ANALYSIS_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "A001", "AI 분석 서버와 통신에 실패했습니다."),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "A003", "분석 결과를 찾을 수 없습니다."),
    DOCUMENT_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "A002", "지원하지 않는 문서 형식입니다."),
	S3_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "I001", "S3 업로드에 실패했습니다."),
	S3_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "I002", "S3 삭제에 실패했습니다."),
	S3_DOWNLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "I003", "S3 다운로드에 실패했습니다."),

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "U001", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "U002", "이메일 또는 비밀번호가 올바르지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "U003", "이메일 인증이 필요합니다."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "U004", "이미 인증된 이메일입니다."),
    EMAIL_VERIFICATION_EXPIRED(HttpStatus.GONE, "U005", "이메일 인증 코드가 만료되었습니다."),
    EMAIL_VERIFICATION_INVALID_CODE(HttpStatus.BAD_REQUEST, "U006", "이메일 인증 코드가 올바르지 않습니다."),
    EMAIL_VERIFICATION_RESEND_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "U007", "이메일 인증 재발송 횟수를 초과했습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U008", "사용자를 찾을 수 없습니다."),
    CURRENT_PASSWORD_INVALID(HttpStatus.UNAUTHORIZED, "U009", "현재 비밀번호가 올바르지 않습니다."),

    REGULATION_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "해당 기준일의 유효한 규정을 찾을 수 없습니다."),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "D001", "문서를 찾을 수 없습니다."),
    SUGGESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Q001", "건의사항을 찾을 수 없습니다."),
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "공지사항을 찾을 수 없습니다."),

    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "C004", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),

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
