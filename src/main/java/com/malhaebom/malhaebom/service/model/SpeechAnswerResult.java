package com.malhaebom.malhaebom.service.model;

import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;

public record SpeechAnswerResult(
	Long speechAnswerId,
	String transcript,
	Double confidence
) {

	public static SpeechAnswerResult from(SpeechAnswer speechAnswer) {
		return new SpeechAnswerResult(
			speechAnswer.getId(),
			speechAnswer.getTranscript(),
			speechAnswer.getConfidence()
		);
	}
}
