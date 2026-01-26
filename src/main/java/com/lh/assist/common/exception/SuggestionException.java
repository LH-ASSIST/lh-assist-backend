package com.lh.assist.common.exception;

public class SuggestionException extends BusinessException {
    public SuggestionException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SuggestionException(ErrorCode errorCode, Throwable cause) {
        super(errorCode);
        initCause(cause);
    }
}