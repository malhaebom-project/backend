package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.QuestionType;

public record QuestionTypeResponse(
	String code,
	String name
) {

	public static QuestionTypeResponse from(QuestionType questionType) {
		return new QuestionTypeResponse(
			questionType.getCode(),
			questionType.getName()
		);
	}
}
