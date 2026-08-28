package com.malhaebom.malhaebom.service.port;

public interface SpeechTranscriptionRateLimit {
	SpeechTranscriptionRateLimit UNLIMITED = () -> true;

	boolean tryAcquire();
}
