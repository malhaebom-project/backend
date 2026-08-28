package com.malhaebom.malhaebom.domain.learning;

public enum AnswerResult {
	CORRECT,
	PARTIALLY_CORRECT,
	INCORRECT,
	UNRECOGNIZED;

	public boolean isCorrect() {
		return this == CORRECT;
	}
}
