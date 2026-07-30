package com.malhaebom.malhaebom.global.exception;

public class InvalidAudioFileException extends ApiException {

	public InvalidAudioFileException() {
		super(ErrorCode.INVALID_AUDIO_FILE);
	}

	public InvalidAudioFileException(String message) {
		super(ErrorCode.INVALID_AUDIO_FILE, message);
	}
}
