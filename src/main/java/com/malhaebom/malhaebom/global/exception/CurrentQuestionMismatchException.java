package com.malhaebom.malhaebom.global.exception;

public class CurrentQuestionMismatchException extends ApiException {

	public CurrentQuestionMismatchException() {
		super(ErrorCode.CURRENT_QUESTION_MISMATCH);
	}
}
