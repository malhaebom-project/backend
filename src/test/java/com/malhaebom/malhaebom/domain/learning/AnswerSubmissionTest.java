package com.malhaebom.malhaebom.domain.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AnswerSubmissionTest {

	@Test
	void 완료된_음성_답변의_제출을_예약하고_채점_입력을_복사한다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		SpeechAnswer speechAnswer = completedSpeechAnswer(sessionQuestion);

		AnswerSubmission submission = AnswerSubmission.reserve(
			sessionQuestion,
			speechAnswer,
			1
		);

		assertSame(sessionQuestion, submission.getSessionQuestion());
		assertSame(speechAnswer, submission.getSpeechAnswer());
		assertEquals(1, submission.getAttemptNo());
		assertEquals(AnswerSubmissionStatus.PENDING, submission.getStatus());
		assertNull(submission.getAnswer());
		assertNull(submission.getProcessingToken());
		assertNull(submission.getLeaseExpiresAt());
		assertNull(submission.getFailureMessage());
		assertEquals(
			"What is the boy doing?",
			submission.getQuestionTextSnapshot()
		);
		assertEquals(
			"남자아이는 무엇을 하고 있나요?",
			submission.getQuestionTextKoSnapshot()
		);
		assertEquals(
			"The boy is running.",
			submission.getModelAnswerSnapshot()
		);
		assertEquals(
			Set.of("He is running.", "He's running."),
			submission.getAcceptedAnswersSnapshot()
		);
		assertEquals(
			"He is running.",
			submission.getAnswerTextSnapshot()
		);
	}

	@Test
	void 예약의_허용_답안_스냅샷은_외부에서_수정할_수_없다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		AnswerSubmission submission = AnswerSubmission.reserve(
			sessionQuestion,
			completedSpeechAnswer(sessionQuestion),
			1
		);

		assertThrows(
			UnsupportedOperationException.class,
			() -> submission.getAcceptedAnswersSnapshot().add("New answer")
		);
	}

	@Test
	void 처리_중인_음성_답변은_제출을_예약할_수_없다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		SpeechAnswer processing = SpeechAnswer.start(
			sessionQuestion,
			"processing-request-key",
			1
		);

		assertThrows(
			IllegalArgumentException.class,
			() -> AnswerSubmission.reserve(sessionQuestion, processing, 1)
		);
	}

	@Test
	void 다른_문제의_음성_답변은_제출을_예약할_수_없다() {
		LearningSessionQuestion currentQuestion = createSessionQuestion();
		LearningSessionQuestion otherQuestion = createSessionQuestion();

		assertThrows(
			IllegalArgumentException.class,
			() -> AnswerSubmission.reserve(
				currentQuestion,
				completedSpeechAnswer(otherQuestion),
				1
			)
		);
	}

	@Test
	void 완료된_문제는_제출을_예약할_수_없다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		SpeechAnswer speechAnswer = completedSpeechAnswer(sessionQuestion);
		sessionQuestion.getLearningSession().completeCurrentQuestion(true);

		assertThrows(
			IllegalStateException.class,
			() -> AnswerSubmission.reserve(
				sessionQuestion,
				speechAnswer,
				1
			)
		);
	}

	@Test
	void 답변_시도_번호는_1_이상이어야_한다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();

		assertThrows(
			IllegalArgumentException.class,
			() -> AnswerSubmission.reserve(
				sessionQuestion,
				completedSpeechAnswer(sessionQuestion),
				0
			)
		);
	}

	private SpeechAnswer completedSpeechAnswer(
		LearningSessionQuestion sessionQuestion
	) {
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			sessionQuestion,
			"request-key",
			1
		);
		speechAnswer.complete("He is running.", 0.94, "TEST_STT");
		return speechAnswer;
	}

	private LearningSessionQuestion createSessionQuestion() {
		Question question = createQuestion("What is the boy doing?");
		LearningSession session = LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(question)
		);
		return session.getCurrentQuestion();
	}

	private Question createQuestion(String questionText) {
		return Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			questionText,
			"남자아이는 무엇을 하고 있나요?",
			null,
			"The boy is running.",
			Set.of("He is running.", "He's running."),
			null,
			null
		);
	}
}
