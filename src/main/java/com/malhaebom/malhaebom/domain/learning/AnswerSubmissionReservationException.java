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
		SESSION_NOT_IN_PROGRESS,
		CURRENT_QUESTION_MISMATCH,
		SPEECH_ANSWER_NOT_COMPLETED,
		ATTEMPT_NOT_ALLOWED
	}
}
