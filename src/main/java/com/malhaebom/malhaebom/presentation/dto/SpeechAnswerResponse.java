package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.service.dto.SpeechAnswerResult;

public record SpeechAnswerResponse(
	Long speechAnswerId,
	String transcript,
	Double confidence,
	String audioUrl
) {

	public static SpeechAnswerResponse from(SpeechAnswerResult result) {
		return new SpeechAnswerResponse(
			result.speechAnswerId(),
			result.transcript(),
			result.confidence(),
			null
		);
	}
}
