package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.service.dto.SpeechAnswerResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "음성 답변 STT 처리 결과")
public record SpeechAnswerResponse(
	@Schema(description = "후속 답변 제출에 사용할 음성 답변 ID", example = "42")
	Long speechAnswerId,

	@Schema(description = "STT로 인식한 영어 답변", example = "It is a cat.")
	String transcript,

	@Schema(description = "STT 인식 신뢰도 (0~1)", example = "0.97", minimum = "0", maximum = "1")
	Double confidence,

	@Schema(description = "녹음 재생 URL. 현재는 제공하지 않으므로 null", nullable = true)
	String audioUrl
) {
	public static SpeechAnswerResponse from(SpeechAnswerResult result) {
		return new SpeechAnswerResponse(result.speechAnswerId(), result.transcript(), result.confidence(), null);
	}
}
