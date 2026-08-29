package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "학습 세션 생성 요청")
public record CreateLearningSessionRequest(
	@NotNull
	@Schema(description = "학습할 어린이 프로필 ID", example = "1")
	Long childId,

	@NotNull
	@Schema(description = "학습 주제 ID (1: 동물, 2: 음식, 3: 일상생활)", example = "1")
	Long topicId,

	@NotNull
	@Schema(description = "문제 난이도", example = "EASY")
	Difficulty difficulty,

	@NotEmpty
	@Schema(
		description = "출제할 문제 유형 목록",
		example = "[\"SHORT_ANSWER\", \"PICTURE_DESCRIPTION\", \"OPEN_SPEAKING\"]"
	)
	List<@NotNull QuestionType> questionTypes,

	@Min(1)
	@Schema(description = "출제할 문제 수", example = "5", minimum = "1")
	int questionCount
) {}
