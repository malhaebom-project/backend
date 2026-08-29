package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.Question;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문제 힌트")
public record HintResponse(
	@Schema(description = "빈칸 형태의 텍스트 힌트", example = "It is a ___.") String hintText,
	@Schema(description = "힌트 음성 URL. 현재는 제공하지 않으므로 null", nullable = true) String hintTtsUrl
) {
	public static HintResponse from(Question question) {
		return new HintResponse(question.getHintText(), null);
	}
}
