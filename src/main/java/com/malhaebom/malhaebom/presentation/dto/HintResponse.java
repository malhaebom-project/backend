package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.Question;

public record HintResponse(
	String hintText,
	String hintTtsUrl
) {

	public static HintResponse from(Question question) {
		return new HintResponse(question.getHintText(), null);
	}
}
