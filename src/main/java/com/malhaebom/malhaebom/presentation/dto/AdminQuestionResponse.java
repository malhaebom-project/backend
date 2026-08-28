package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;

@Schema(description = "관리자용 문제 상세 정보")
public record AdminQuestionResponse(
	@Schema(description = "문제 ID", example = "1")
	Long questionId,
	@Schema(description = "학습 주제", example = "ANIMAL")
	LearningTopic topic,
	@Schema(description = "난이도", example = "EASY")
	Difficulty difficulty,
	@Schema(description = "문제 유형", example = "SHORT_ANSWER")
	QuestionType type,
	@Schema(description = "영문 문제", example = "What animal is this?")
	String questionText,
	@Schema(description = "한국어 문제", example = "이 동물은 무엇일까요?")
	String questionTextKo,
	@Schema(description = "문제 이미지 URL", example = "https://malhaebom-assets-prod-514090179227-ap-northeast-2.an.s3.ap-northeast-2.amazonaws.com/question-images/easy/animal/short-answer/cat.webp")
	String imageUrl,
	@Schema(description = "AI 채점에 제공하는 문제 맥락", example = "The picture shows a cat sitting by itself.")
	String gradingContext,
	@Schema(description = "대표 모범 답안", example = "It is a cat.")
	String modelAnswer,
	@Schema(description = "허용 답안 목록", example = "[\"a cat\", \"cat\", \"It is a kitty\", \"It's a cat\", \"kitty\"]")
	Set<String> acceptedAnswers,
	@Schema(description = "텍스트 힌트", example = "It is a ___.")
	String hintText,
	@Schema(description = "문제 음성 URL", example = "https://malhaebom-assets-prod-514090179227-ap-northeast-2.an.s3.ap-northeast-2.amazonaws.com/tts/questions/1.mp3", nullable = true)
	String ttsUrl,
	@Schema(description = "출제 활성화 여부", example = "true")
	boolean active,
	@Schema(description = "생성 시각 (UTC)", example = "2026-07-01T00:00:00Z", format = "date-time")
	Instant createdAt,
	@Schema(description = "수정 시각 (UTC)", example = "2026-08-01T00:00:00Z", format = "date-time")
	Instant updatedAt
) {
	public static AdminQuestionResponse from(Question question, String imageUrl) {
		return new AdminQuestionResponse(
			question.getId(),
			question.getTopic(),
			question.getDifficulty(),
			question.getType(),
			question.getQuestionText(),
			question.getQuestionTextKo(),
			imageUrl,
			question.getGradingContext(),
			question.getModelAnswer(),
			question.getAcceptedAnswers(),
			question.getHintText(),
			question.getTtsUrl(),
			question.isActive(),
			question.getCreatedAt(),
			question.getUpdatedAt()
		);
	}
}
