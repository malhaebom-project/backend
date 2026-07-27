package com.malhaebom.malhaebom.global.exception;

public class LearningSessionNotFoundException extends RuntimeException {

	public LearningSessionNotFoundException() {
		super("학습 세션을 찾을 수 없습니다.");
	}
}
