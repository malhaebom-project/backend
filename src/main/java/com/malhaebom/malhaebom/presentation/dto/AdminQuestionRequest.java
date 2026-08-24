package com.malhaebom.malhaebom.presentation.dto;

import java.util.Set;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.service.dto.AdminQuestionCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminQuestionRequest(
	@NotNull LearningTopic topic,
	@NotNull Difficulty difficulty,
	@NotNull QuestionType type,

	@NotBlank
	@Size(max = 500)
	String questionText,

	@NotBlank
	@Size(max = 500)
	String questionTextKo,

	@Size(max = 2048)
	String imageUrl,

	@Size(max = 2000)
	String gradingContext,

	@NotBlank
	@Size(max = 1000)
	String modelAnswer,

	@NotNull
	Set<
		@NotBlank
		@Size(max = 1000)
		String
	> acceptedAnswers,

	@Size(max = 500)
	String hintText
) {

	public AdminQuestionCommand toCommand() {
		return new AdminQuestionCommand(
			topic,
			difficulty,
			type,
			questionText,
			questionTextKo,
			imageUrl,
			gradingContext,
			modelAnswer,
			acceptedAnswers,
			hintText
		);
	}
}
