package com.malhaebom.malhaebom.global.exception;

public class LearningSessionNotFoundException extends ApiException {

	public LearningSessionNotFoundException() {
		super(ErrorCode.LEARNING_SESSION_NOT_FOUND);
	}
}
