package com.lh.assist.common.exception;

public class ChatbotException extends BusinessException {

    public ChatbotException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ChatbotException(ErrorCode errorCode, Throwable cause) {
        super(errorCode);
        initCause(cause);
    }
}