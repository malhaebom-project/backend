package com.malhaebom.malhaebom.presentation.dto;

import java.util.List;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.QuestionType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateLearningSessionRequest(
	@NotNull Long childId,
	@NotNull Long topicId,
	@NotNull Difficulty difficulty,
	@NotEmpty List<@NotNull QuestionType> questionTypes,
	@Min(1) int questionCount
) {}
