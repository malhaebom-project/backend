package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "학습 세션의 다음 문제")
public record NextQuestionResponse(
	@Schema(description = "세션에 배정된 문제 ID", example = "12")
	Long sessionQuestionId,
	@Schema(description = "원본 문제 ID", example = "1")
	Long questionId,
	@Schema(description = "사용자에게 표시할 1 기반 문제 순서", example = "1")
	int questionIndex,
	@Schema(description = "세션의 전체 문제 수", example = "5")
	int totalQuestionCount,
	@Schema(description = "문제 유형", example = "SHORT_ANSWER")
	QuestionType type,
	@Schema(description = "영문 문제", example = "What animal is this?")
	String questionText,
	@Schema(description = "한국어 문제", example = "이 동물은 무엇일까요?")
	String questionTextKo,
	@Schema(description = "문제 이미지 URL", example = "https://malhaebom-assets-prod-514090179227-ap-northeast-2.an.s3.ap-northeast-2.amazonaws.com/question-images/easy/animal/short-answer/cat.webp")
	String imageUrl,
	@Schema(description = "문제 힌트", example = "It is a ___.")
	String hintText,
	@Schema(description = "문제 음성 URL", example = "https://malhaebom-assets-prod-514090179227-ap-northeast-2.an.s3.ap-northeast-2.amazonaws.com/tts/questions/1.mp3", nullable = true)
	String ttsUrl
) {
	public static NextQuestionResponse from(LearningSessionQuestion sessionQuestion, String imageUrl) {
		LearningSession session = sessionQuestion.getLearningSession();
		Question question = sessionQuestion.getQuestion();

		return new NextQuestionResponse(
			sessionQuestion.getId(),
			question.getId(),
			sessionQuestion.getQuestionIndex() + 1,
			session.getQuestionCount(),
			question.getType(),
			question.getQuestionText(),
			question.getQuestionTextKo(),
			imageUrl,
			question.getHintText(),
			question.getTtsUrl()
		);
	}
}
