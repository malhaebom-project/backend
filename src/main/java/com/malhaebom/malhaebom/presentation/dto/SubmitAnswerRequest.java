package com.malhaebom.malhaebom.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "학습 답변 제출 요청")
public record SubmitAnswerRequest(
	@NotNull
	@Schema(
		description = "음성 답변 업로드 결과로 발급된 ID. 서버에 저장된 STT 문장을 채점하므로 별도의 answerText는 받지 않습니다.",
		example = "42"
	)
	Long speechAnswerId
) {}
