package com.malhaebom.malhaebom.global.exception;

public class UnauthorizedException extends ApiException {

	public UnauthorizedException(String message) {
		super(ErrorCode.UNAUTHORIZED, message);
	}

	public UnauthorizedException(String message, Throwable cause) {
		super(ErrorCode.UNAUTHORIZED, message, cause);
	}
}
