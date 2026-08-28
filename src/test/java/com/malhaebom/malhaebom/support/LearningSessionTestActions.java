package com.malhaebom.malhaebom.support;

import com.malhaebom.malhaebom.domain.learning.*;

import java.util.concurrent.atomic.AtomicLong;

public final class LearningSessionTestActions {
	private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();

	private LearningSessionTestActions() {}

	public static void completeCurrentQuestion(
		LearningSession session,
		boolean correct
	) {
		LearningSessionQuestion sessionQuestion = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			sessionQuestion,
			"test-completion-" + REQUEST_SEQUENCE.incrementAndGet(),
			1
		);
		speechAnswer.complete("test answer", 1.0, "TEST_STT");
		Answer answer = Answer.create(
			sessionQuestion,
			speechAnswer,
			1,
			AnswerEvaluation.from(
				correct ? AnswerResult.CORRECT : AnswerResult.INCORRECT
			),
			"test feedback"
		);

		session.applyAnswerResult(answer);
		if (!correct) {
			session.skipRetry(answer);
		}
	}
}
