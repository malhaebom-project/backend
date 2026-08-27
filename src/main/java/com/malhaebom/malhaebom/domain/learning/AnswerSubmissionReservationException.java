package com.malhaebom.malhaebom.domain.learning;

import lombok.Getter;

@Getter
public class AnswerSubmissionReservationException extends IllegalStateException {

	private final Reason reason;

	public AnswerSubmissionReservationException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public enum Reason {
		SPEECH_ANSWER_NOT_COMPLETED,
		SPEECH_ANSWER_QUESTION_MISMATCH,
		ATTEMPT_NOT_ALLOWED
	}
}
