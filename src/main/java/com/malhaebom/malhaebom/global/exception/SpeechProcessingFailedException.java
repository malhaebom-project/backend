package com.malhaebom.malhaebom.global.exception;

public class SpeechProcessingFailedException extends ApiException {

	public SpeechProcessingFailedException() {
		super(ErrorCode.STT_PROCESSING_FAILED);
	}

	public SpeechProcessingFailedException(Throwable cause) {
		super(ErrorCode.STT_PROCESSING_FAILED, cause);
	}
}
