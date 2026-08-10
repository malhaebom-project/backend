package com.malhaebom.malhaebom.domain.learning;

public record AnswerEvaluation(
	AnswerResult result,
	int meaningScore,
	int expressionScore,
	int grammarScore
) {

	private static final int MAX_MEANING_SCORE = 50;
	private static final int MAX_EXPRESSION_SCORE = 30;
	private static final int MAX_GRAMMAR_SCORE = 20;

	public AnswerEvaluation {
		if (result == null) {
			throw new IllegalArgumentException("답변 결과는 null일 수 없습니다.");
		}

		validateScore("의미 전달 점수", meaningScore, MAX_MEANING_SCORE);
		validateScore(
			"표현 적절성 점수",
			expressionScore,
			MAX_EXPRESSION_SCORE
		);
		validateScore("문법 점수", grammarScore, MAX_GRAMMAR_SCORE);
	}

	public int score() {
		return meaningScore + expressionScore + grammarScore;
	}

	public static AnswerEvaluation from(AnswerResult result) {
		if (result == null) {
			throw new IllegalArgumentException("답변 결과는 null일 수 없습니다.");
		}

		return switch (result) {
			case CORRECT -> new AnswerEvaluation(result, 50, 30, 20);
			case PARTIALLY_CORRECT -> new AnswerEvaluation(result, 25, 15, 10);
			case INCORRECT, UNRECOGNIZED ->
				new AnswerEvaluation(result, 0, 0, 0);
		};
	}

	private static void validateScore(
		String scoreName,
		int score,
		int maximum
	) {
		if (score < 0 || score > maximum) {
			throw new IllegalArgumentException(
				scoreName + "는 0점 이상 " + maximum + "점 이하여야 합니다."
			);
		}
	}
}
