package com.lh.assist.common.exception;

import lombok.Getter;

@Getter
public class SystemException extends RuntimeException {
	private final ErrorCode errorCode;

	public SystemException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public SystemException(ErrorCode errorCode, Throwable cause) {
		super(errorCode.getMessage(), cause);
		this.errorCode = errorCode;
	}
}