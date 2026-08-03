package com.malhaebom.malhaebom.global.exception;

public class ForbiddenException extends ApiException {

	public ForbiddenException(String message) {
		super(ErrorCode.FORBIDDEN, message);
	}
}
