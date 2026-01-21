package com.lh.assist.common.exception;

public class DocumentException extends BusinessException {

    public DocumentException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DocumentException(ErrorCode errorCode, Throwable cause) {
        super(errorCode);
        initCause(cause);
    }
}