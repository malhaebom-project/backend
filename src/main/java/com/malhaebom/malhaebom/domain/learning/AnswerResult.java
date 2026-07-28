package com.malhaebom.malhaebom.domain.learning;

import lombok.Getter;

@Getter
public enum AnswerResult {

	CORRECT(100),
	INCORRECT(0);

	private final int score;

	AnswerResult(int score) {
		this.score = score;
	}

	public boolean isCorrect() {
		return this == CORRECT;
	}
}
