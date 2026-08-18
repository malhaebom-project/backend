package com.malhaebom.malhaebom.presentation.dto;

import java.time.LocalDateTime;

import com.malhaebom.malhaebom.service.dto.WrongAnswer;

public record WrongAnswerResponse(
	Long answerId,
	Long questionId,
	String questionText,
	String imageUrl,
	String answerText,
	String modelAnswer,
	String feedbackText,
	LocalDateTime answeredAt
) {

	public static WrongAnswerResponse from(WrongAnswer wrongAnswer) {
		return new WrongAnswerResponse(
			wrongAnswer.answerId(),
			wrongAnswer.questionId(),
			wrongAnswer.questionText(),
			wrongAnswer.imageUrl(),
			wrongAnswer.answerText(),
			wrongAnswer.modelAnswer(),
			wrongAnswer.feedbackText(),
			wrongAnswer.answeredAt()
		);
	}
}
