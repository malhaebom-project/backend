package com.malhaebom.malhaebom.domain.learning;

import lombok.Getter;

@Getter
public enum AnswerResult {

	CORRECT(100),
	PARTIALLY_CORRECT(50),
	INCORRECT(0),
	UNRECOGNIZED(0);

	private final int defaultScore;

	AnswerResult(int defaultScore) {
		this.defaultScore = defaultScore;
	}

	public boolean isCorrect() {
		return this == CORRECT;
	}
}
