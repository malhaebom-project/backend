package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.service.dto.WrongAnswer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "최근 오답")
public record WrongAnswerResponse(
	@Schema(description = "저장된 답변 ID", example = "101")
	Long answerId,
	@Schema(description = "원본 문제 ID", example = "1")
	Long questionId,
	@Schema(description = "영문 문제", example = "What animal is this?")
	String questionText,
	@Schema(description = "문제 이미지 URL", example = "https://malhaebom-assets-prod-514090179227-ap-northeast-2.an.s3.ap-northeast-2.amazonaws.com/question-images/easy/animal/short-answer/cat.webp")
	String imageUrl,
	@Schema(description = "학습자가 말한 답변", example = "It is a dog.")
	String answerText,
	@Schema(description = "문제의 대표 모범 답안", example = "It is a cat.")
	String modelAnswer,
	@Schema(description = "채점 피드백", example = "사진 속 동물은 고양이예요. 다시 한번 말해 보세요.")
	String feedbackText,
	@Schema(description = "답변 시각 (UTC)", example = "2026-08-01T01:05:00Z", format = "date-time")
	OffsetDateTime answeredAt
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
			wrongAnswer.answeredAt().atOffset(ZoneOffset.UTC)
		);
	}
}
