package com.malhaebom.malhaebom.domain.learning;

import lombok.Getter;

@Getter
public class LearningSessionAnswerSubmissionException
	extends IllegalStateException {

	private final Reason reason;

	public LearningSessionAnswerSubmissionException(
		Reason reason,
		String message
	) {
		super(message);
		this.reason = reason;
	}

	public enum Reason {
		SESSION_NOT_IN_PROGRESS,
		CURRENT_QUESTION_MISMATCH
	}
}
