package com.lh.assist.common.exception;

public class AuthException extends BusinessException {

    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(ErrorCode errorCode, Throwable cause) {
        super(errorCode);
        initCause(cause);
    }
}