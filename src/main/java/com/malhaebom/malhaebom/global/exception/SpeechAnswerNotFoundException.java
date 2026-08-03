package com.malhaebom.malhaebom.global.exception;

public class SpeechAnswerNotFoundException extends ApiException {

	public SpeechAnswerNotFoundException() {
		super(ErrorCode.SPEECH_ANSWER_NOT_FOUND);
	}
}
