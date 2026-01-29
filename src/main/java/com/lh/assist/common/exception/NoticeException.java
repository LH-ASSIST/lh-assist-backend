package com.lh.assist.common.exception;

public class NoticeException extends BusinessException {
    public NoticeException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NoticeException(ErrorCode errorCode, Throwable cause) {
        super(errorCode);
        initCause(cause);
    }
}