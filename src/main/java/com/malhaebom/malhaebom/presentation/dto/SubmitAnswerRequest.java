package com.malhaebom.malhaebom.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitAnswerRequest(
	@NotNull
	Long speechAnswerId,

	@NotBlank
	@Size(max = 4000)
	String answerText
) {
}
