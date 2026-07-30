package com.malhaebom.malhaebom.service.model;

public record SpeechTranscriptionResult(
	String transcript,
	Double confidence,
	String provider
) {
}
