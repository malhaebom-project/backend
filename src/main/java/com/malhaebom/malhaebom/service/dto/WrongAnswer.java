package com.malhaebom.malhaebom.service.dto;

import java.time.LocalDateTime;

public record WrongAnswer(
	Long answerId,
	Long questionId,
	String questionText,
	String imageUrl,
	String answerText,
	String modelAnswer,
	String feedbackText,
	LocalDateTime answeredAt
) {
}
