package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.Question;

public record QuestionTtsResponse(Long questionId, String text, String audioUrl) {
	public static QuestionTtsResponse from(Question question) {
		return new QuestionTtsResponse(question.getId(), question.getQuestionText(), question.getTtsUrl());
	}
}
