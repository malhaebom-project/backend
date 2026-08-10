package com.malhaebom.malhaebom.service.dto;

import java.util.LinkedHashSet;
import java.util.List;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;

public record AnswerAssessment(
	boolean recognized,
	int meaningScore,
	int expressionScore,
	int grammarScore,
	List<String> matchedKeywords,
	List<String> missingKeywords,
	String feedbackText
) {

	private static final int MAX_MEANING_SCORE = 50;
	private static final int MAX_EXPRESSION_SCORE = 30;
	private static final int MAX_GRAMMAR_SCORE = 20;
	private static final int MAX_KEYWORDS = 3;
	private static final int MAX_FEEDBACK_LENGTH = 300;

	public AnswerAssessment {
		validateScore("의미 전달 점수", meaningScore, MAX_MEANING_SCORE);
		validateScore("표현 적절성 점수", expressionScore, MAX_EXPRESSION_SCORE);
		validateScore("문법 점수", grammarScore, MAX_GRAMMAR_SCORE);

		if (!recognized && (meaningScore + expressionScore + grammarScore > 0)) {
			throw new IllegalArgumentException(
				"인식되지 않은 답변의 세부 점수는 모두 0이어야 합니다."
			);
		}

		matchedKeywords = normalizeKeywords(matchedKeywords);
		missingKeywords = normalizeKeywords(missingKeywords);
		feedbackText = normalizeFeedback(feedbackText);
	}

	public int totalScore() {
		return meaningScore + expressionScore + grammarScore;
	}

	public AnswerResult result() {
		if (!recognized) {
			return AnswerResult.UNRECOGNIZED;
		}

		if (totalScore() >= 80 && missingKeywords.isEmpty()) {
			return AnswerResult.CORRECT;
		}

		if (totalScore() >= 40) {
			return AnswerResult.PARTIALLY_CORRECT;
		}

		return AnswerResult.INCORRECT;
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

	private static List<String> normalizeKeywords(List<String> keywords) {
		if (keywords == null) {
			return List.of();
		}

		return keywords.stream()
			.filter(keyword -> keyword != null && !keyword.isBlank())
			.map(String::strip)
			.collect(
				LinkedHashSet<String>::new,
				LinkedHashSet::add,
				LinkedHashSet::addAll
			)
			.stream()
			.limit(MAX_KEYWORDS)
			.toList();
	}

	private static String normalizeFeedback(String feedbackText) {
		if (feedbackText == null || feedbackText.isBlank()) {
			throw new IllegalArgumentException("피드백은 비어 있을 수 없습니다.");
		}

		String normalized = feedbackText.strip();
		if (normalized.length() <= MAX_FEEDBACK_LENGTH) {
			return normalized;
		}

		return normalized.substring(0, MAX_FEEDBACK_LENGTH);
	}
}
