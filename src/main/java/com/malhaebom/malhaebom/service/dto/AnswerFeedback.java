package com.malhaebom.malhaebom.service.dto;

import java.util.List;
import java.util.Objects;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;

public record AnswerFeedback(
	List<String> matchedKeywords,
	List<String> missingKeywords,
	String feedbackText
) {

	private static final int MAX_KEYWORD_COUNT = 3;
	private static final int MAX_KEYWORD_LENGTH = 80;
	private static final int MAX_FEEDBACK_LENGTH = 300;
	private static final String CORRECT_FALLBACK =
		"정확하고 또박또박 잘 말했어요!";
	private static final String RETRY_FALLBACK =
		"좋은 시도예요! 모범 답안을 참고해서 다시 말해 보세요.";

	public AnswerFeedback {
		matchedKeywords = normalizeKeywords(matchedKeywords);
		missingKeywords = normalizeKeywords(missingKeywords);
		feedbackText = normalizeText(feedbackText, MAX_FEEDBACK_LENGTH);
	}

	public static AnswerFeedback fallback(AnswerResult result) {
		String feedbackText = result != null && result.isCorrect()
			? CORRECT_FALLBACK
			: RETRY_FALLBACK;
		return new AnswerFeedback(List.of(), List.of(), feedbackText);
	}

	public boolean hasFeedbackText() {
		return feedbackText != null && !feedbackText.isBlank();
	}

	private static List<String> normalizeKeywords(List<String> keywords) {
		if (keywords == null) {
			return List.of();
		}

		return keywords.stream()
			.filter(Objects::nonNull)
			.map(keyword -> normalizeText(keyword, MAX_KEYWORD_LENGTH))
			.filter(Objects::nonNull)
			.distinct()
			.limit(MAX_KEYWORD_COUNT)
			.toList();
	}

	private static String normalizeText(String text, int maxLength) {
		if (text == null || text.isBlank()) {
			return null;
		}

		String normalized = text.strip();
		return normalized.length() <= maxLength
			? normalized
			: normalized.substring(0, maxLength);
	}
}
