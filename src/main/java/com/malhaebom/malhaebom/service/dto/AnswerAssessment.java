package com.malhaebom.malhaebom.service.dto;

import java.util.Objects;

import com.malhaebom.malhaebom.domain.learning.AnswerEvaluation;
import com.malhaebom.malhaebom.domain.learning.AnswerResult;

public record AnswerAssessment(
	boolean recognized,
	int meaningScore,
	int expressionScore,
	int grammarScore,
	String feedbackText
) {

	private static final int MAX_MEANING_SCORE = 50;
	private static final int MAX_EXPRESSION_SCORE = 30;
	private static final int MAX_GRAMMAR_SCORE = 20;
	private static final int MAX_FEEDBACK_LENGTH = 300;
	private static final String CORRECT_FALLBACK =
		"정확하고 또박또박 잘 말했어요!";
	private static final String RETRY_FALLBACK =
		"좋은 시도예요! 모범 답안을 참고해서 다시 말해 보세요.";

	public AnswerAssessment {
		validateScore("의미 전달 점수", meaningScore, MAX_MEANING_SCORE);
		validateScore("표현 적절성 점수", expressionScore, MAX_EXPRESSION_SCORE);
		validateScore("문법 점수", grammarScore, MAX_GRAMMAR_SCORE);

		if (!recognized && (meaningScore + expressionScore + grammarScore > 0)) {
			throw new IllegalArgumentException(
				"인식되지 않은 답변의 세부 점수는 모두 0이어야 합니다."
			);
		}

		feedbackText = normalizeFeedback(feedbackText);
	}

	public static AnswerAssessment fallback(AnswerEvaluation evaluation) {
		Objects.requireNonNull(evaluation, "평가 결과는 null일 수 없습니다.");
		String feedbackText = evaluation.result().isCorrect()
			? CORRECT_FALLBACK
			: RETRY_FALLBACK;

		return new AnswerAssessment(
			evaluation.result() != AnswerResult.UNRECOGNIZED,
			evaluation.meaningScore(),
			evaluation.expressionScore(),
			evaluation.grammarScore(),
			feedbackText
		);
	}

	public int totalScore() {
		return meaningScore + expressionScore + grammarScore;
	}

	public AnswerResult result() {
		if (!recognized) {
			return AnswerResult.UNRECOGNIZED;
		}

		if (meaningScore >= 40 && totalScore() >= 80) {
			return AnswerResult.CORRECT;
		}

		if (meaningScore >= 30 && totalScore() >= 40) {
			return AnswerResult.PARTIALLY_CORRECT;
		}

		return AnswerResult.INCORRECT;
	}

	public AnswerEvaluation toEvaluation() {
		return new AnswerEvaluation(
			result(),
			meaningScore,
			expressionScore,
			grammarScore
		);
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

	private static String normalizeFeedback(String feedbackText) {
		String normalized = normalizeText(feedbackText, MAX_FEEDBACK_LENGTH);
		if (normalized == null) {
			throw new IllegalArgumentException("피드백은 비어 있을 수 없습니다.");
		}

		return normalized;
	}

	private static String normalizeText(String text, int maximumLength) {
		if (text == null || text.isBlank()) {
			return null;
		}

		String normalized = text.strip();
		return normalized.length() <= maximumLength
			? normalized
			: normalized.substring(0, maximumLength);
	}
}
