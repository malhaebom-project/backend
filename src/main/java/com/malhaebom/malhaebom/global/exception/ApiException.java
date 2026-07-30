package com.malhaebom.malhaebom.global.exception;

import lombok.Getter;

@Getter
public abstract class ApiException extends RuntimeException {

	private final ErrorCode errorCode;

	protected ApiException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	protected ApiException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	protected ApiException(ErrorCode errorCode, Throwable cause) {
		super(errorCode.getMessage(), cause);
		this.errorCode = errorCode;
	}

	protected ApiException(
		ErrorCode errorCode,
		String message,
		Throwable cause
	) {
		super(message, cause);
		this.errorCode = errorCode;
	}

}
