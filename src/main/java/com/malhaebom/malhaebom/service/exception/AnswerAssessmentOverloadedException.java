package com.malhaebom.malhaebom.service.exception;

public class AnswerAssessmentOverloadedException extends RuntimeException {

	public AnswerAssessmentOverloadedException() {
		super("답변 채점 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
	}
}
