package com.malhaebom.malhaebom.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "학습 답변 제출 요청")
public record SubmitAnswerRequest(
	@NotNull
	@Schema(description = "음성 답변 업로드 결과로 발급된 ID", example = "42")
	Long speechAnswerId
) {}
