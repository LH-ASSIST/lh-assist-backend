package com.lh.assist.common.exception;

public class DocumentApprovalException extends BusinessException {

    public DocumentApprovalException(ErrorCode errorCode) {
        super(errorCode);
    }
}