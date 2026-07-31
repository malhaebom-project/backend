package com.malhaebom.malhaebom.global.exception;

public class QuestionNotFoundException extends ApiException {

	public QuestionNotFoundException() {
		super(ErrorCode.QUESTION_NOT_FOUND);
	}
}
