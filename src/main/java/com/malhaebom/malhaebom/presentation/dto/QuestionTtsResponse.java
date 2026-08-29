package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.Question;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문제 문장 음성 정보")
public record QuestionTtsResponse(
	@Schema(description = "문제 ID", example = "1") Long questionId,
	@Schema(description = "음성으로 재생할 영문 문제", example = "What animal is this?") String text,
	@Schema(description = "문제 음성 파일 URL", example = "https://malhaebom-assets-prod-514090179227-ap-northeast-2.an.s3.ap-northeast-2.amazonaws.com/tts/questions/1.mp3") String audioUrl
) {
	public static QuestionTtsResponse from(Question question) {
		return new QuestionTtsResponse(question.getId(), question.getQuestionText(), question.getTtsUrl());
	}
}
