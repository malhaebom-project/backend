package com.malhaebom.malhaebom.global.exception;

public class SpeechTranscriptionTimeoutException extends ApiException {

	public SpeechTranscriptionTimeoutException() {
		super(ErrorCode.STT_PROCESSING_TIMEOUT);
	}

	public SpeechTranscriptionTimeoutException(Throwable cause) {
		super(ErrorCode.STT_PROCESSING_TIMEOUT, cause);
	}
}
