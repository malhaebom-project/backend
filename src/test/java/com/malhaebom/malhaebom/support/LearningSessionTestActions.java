package com.malhaebom.malhaebom.support;

import java.util.concurrent.atomic.AtomicLong;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.AnswerEvaluation;
import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;

public final class LearningSessionTestActions {

	private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();

	private LearningSessionTestActions() {
	}

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
