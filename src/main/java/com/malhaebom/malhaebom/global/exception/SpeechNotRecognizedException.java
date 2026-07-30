package com.malhaebom.malhaebom.global.exception;

public class SpeechNotRecognizedException extends ApiException {

	public SpeechNotRecognizedException() {
		super(ErrorCode.SPEECH_NOT_RECOGNIZED);
	}
}
