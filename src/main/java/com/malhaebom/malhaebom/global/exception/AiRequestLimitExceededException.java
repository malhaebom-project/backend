package com.malhaebom.malhaebom.global.exception;

public class AiRequestLimitExceededException extends ApiException {

	public AiRequestLimitExceededException() {
		super(ErrorCode.AI_REQUEST_LIMIT_EXCEEDED);
	}

	public AiRequestLimitExceededException(Throwable cause) {
		super(ErrorCode.AI_REQUEST_LIMIT_EXCEEDED, cause);
	}
}
