package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.service.dto.AdminQuestionCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(description = "관리자 문제 등록 및 수정 요청")
public record AdminQuestionRequest(
	@NotNull
	@Schema(description = "학습 주제", example = "ANIMAL")
	LearningTopic topic,

	@NotNull
	@Schema(description = "문제 난이도", example = "EASY")
	Difficulty difficulty,

	@NotNull
	@Schema(description = "문제 유형", example = "SHORT_ANSWER")
	QuestionType type,

	@NotBlank
	@Size(max = 500)
	@Schema(description = "학습자에게 제시할 영어 문제", example = "What animal is this?", maxLength = 500)
	String questionText,

	@NotBlank
	@Size(max = 500)
	@Schema(description = "영어 문제의 한국어 안내 문구", example = "이 동물은 무엇일까요?", maxLength = 500)
	String questionTextKo,

	@Size(max = 2048)
	@Schema(
		description = "문제 이미지 경로. 이미지가 없으면 생략할 수 있습니다.",
		example = "/question-images/easy/animal/short-answer/cat.webp",
		maxLength = 2048
	)
	String imageUrl,

	@Size(max = 2000)
	@Schema(description = "AI 채점에 제공할 추가 문맥", example = "The picture shows a cat sitting by itself.", maxLength = 2000)
	String gradingContext,

	@NotBlank
	@Size(max = 1000)
	@Schema(description = "대표 모범 답안", example = "It is a cat.", maxLength = 1000)
	String modelAnswer,

	@NotNull
	@Schema(
		description = "정답으로 인정할 표현 목록",
		example = "[\"a cat\", \"cat\", \"It is a kitty\", \"It's a cat\", \"kitty\"]"
	)
	Set<
		@NotBlank
		@Size(max = 1000)
		String
	> acceptedAnswers,

	@Size(max = 500)
	@Schema(description = "학습자에게 제공할 힌트", example = "It is a ___.", maxLength = 500)
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
