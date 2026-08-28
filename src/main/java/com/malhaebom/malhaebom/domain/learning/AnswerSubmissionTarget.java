package com.malhaebom.malhaebom.domain.learning;

public final class AnswerSubmissionTarget {
	private final LearningSessionQuestion sessionQuestion;

	AnswerSubmissionTarget(LearningSessionQuestion sessionQuestion) {
		this.sessionQuestion = sessionQuestion;
	}

	public AnswerSubmission reserve(SpeechAnswer speechAnswer, int attemptNo) {
		return AnswerSubmission.reserve(sessionQuestion, speechAnswer, attemptNo);
	}
}
