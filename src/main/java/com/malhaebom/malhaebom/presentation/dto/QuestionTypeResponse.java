package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지원하는 문제 유형")
public record QuestionTypeResponse(
	@Schema(description = "문제 유형 코드", example = "SHORT_ANSWER") String code,
	@Schema(description = "문제 유형 표시 이름", example = "주관식") String name
) {
	public static QuestionTypeResponse from(QuestionType questionType) {
		return new QuestionTypeResponse(questionType.getCode(), questionType.getName());
	}
}
