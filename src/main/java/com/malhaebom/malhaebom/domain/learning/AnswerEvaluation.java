package com.malhaebom.malhaebom.domain.learning;

public record AnswerEvaluation(
	AnswerResult result,
	int score
) {

	public AnswerEvaluation {
		if (result == null) {
			throw new IllegalArgumentException("답변 결과는 null일 수 없습니다.");
		}

		if (score < 0 || score > 100) {
			throw new IllegalArgumentException(
				"답변 점수는 0 이상 100 이하여야 합니다."
			);
		}
	}

	public static AnswerEvaluation from(AnswerResult result) {
		if (result == null) {
			throw new IllegalArgumentException("답변 결과는 null일 수 없습니다.");
		}

		return new AnswerEvaluation(result, result.getDefaultScore());
	}
}
