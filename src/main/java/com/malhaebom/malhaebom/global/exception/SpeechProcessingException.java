package com.malhaebom.malhaebom.global.exception;

public class SpeechProcessingException extends ApiException {

	public SpeechProcessingException() {
		super(ErrorCode.SPEECH_PROCESSING);
	}
}
