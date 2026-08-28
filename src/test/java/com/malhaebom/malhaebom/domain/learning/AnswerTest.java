package com.malhaebom.malhaebom.domain.learning;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.malhaebom.malhaebom.support.LearningSessionTestActions.completeCurrentQuestion;
import static org.junit.jupiter.api.Assertions.*;

class AnswerTest {
	private static final String FEEDBACK_TEXT = "현재진행형을 정확하게 사용했어요!";

	@Test
	void 정답_평가_결과로_답변_시도를_생성한다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		SpeechAnswer speechAnswer = completedSpeechAnswer(
			sessionQuestion,
			"The boy is running."
		);

		Answer answer = Answer.create(
			sessionQuestion,
			speechAnswer,
			1,
			AnswerEvaluation.from(AnswerResult.CORRECT),
			FEEDBACK_TEXT
		);

		assertSame(sessionQuestion, answer.getSessionQuestion());
		assertSame(speechAnswer, answer.getSpeechAnswer());
		assertEquals(1, answer.getAttemptNo());
		assertEquals("The boy is running.", answer.getAnswerText());
		assertEquals(AnswerResult.CORRECT, answer.getResult());
		assertEquals(100, answer.getScore());
		assertEquals(50, answer.getMeaningScore());
		assertEquals(30, answer.getExpressionScore());
		assertEquals(20, answer.getGrammarScore());
		assertEquals("The boy is running.", answer.getModelAnswerSnapshot());
		assertEquals(FEEDBACK_TEXT, answer.getFeedbackText());
		assertNotNull(answer.getSubmittedAt());
		assertTrue(answer.isCorrect());
	}

	@Test
	void 부분_정답의_동적_점수를_저장한다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		Answer answer = Answer.create(
			sessionQuestion,
			completedSpeechAnswer(sessionQuestion, "He's running."),
			1,
			new AnswerEvaluation(
				AnswerResult.PARTIALLY_CORRECT,
				40,
				23,
				15
			),
			FEEDBACK_TEXT
		);

		assertEquals(AnswerResult.PARTIALLY_CORRECT, answer.getResult());
		assertEquals(78, answer.getScore());
		assertEquals(40, answer.getMeaningScore());
		assertEquals(23, answer.getExpressionScore());
		assertEquals(15, answer.getGrammarScore());
		assertFalse(answer.isCorrect());
	}

	@Test
	void 세부_점수가_허용_범위를_벗어나면_평가를_생성할_수_없다() {
		assertThrows(
			IllegalArgumentException.class,
			() -> new AnswerEvaluation(
				AnswerResult.PARTIALLY_CORRECT,
				51,
				0,
				0
			)
		);
	}

	@Test
	void 세션_문제_없이_답변을_생성할_수_없다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		SpeechAnswer speechAnswer = completedSpeechAnswer(
			sessionQuestion,
			"The boy is running."
		);

		assertThrows(
			IllegalArgumentException.class,
			() -> Answer.create(
				null,
				speechAnswer,
				1,
				AnswerEvaluation.from(AnswerResult.CORRECT),
				FEEDBACK_TEXT
			)
		);
	}

	@Test
	void 완료한_문제에는_답변을_생성할_수_없다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		SpeechAnswer speechAnswer = completedSpeechAnswer(
			sessionQuestion,
			"The boy is running."
		);
		completeCurrentQuestion(sessionQuestion.getLearningSession(), true);

		assertThrows(
			IllegalStateException.class,
			() -> Answer.create(
				sessionQuestion,
				speechAnswer,
				2,
				AnswerEvaluation.from(AnswerResult.CORRECT),
				FEEDBACK_TEXT
			)
		);
	}

	@Test
	void 처리가_완료되지_않은_음성_답변으로_답변을_생성할_수_없다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			sessionQuestion,
			"request-key",
			1
		);

		assertThrows(
			IllegalArgumentException.class,
			() -> Answer.create(
				sessionQuestion,
				speechAnswer,
				1,
				AnswerEvaluation.from(AnswerResult.INCORRECT),
				FEEDBACK_TEXT
			)
		);
	}

	@Test
	void 다른_문제의_음성_답변으로_답변을_생성할_수_없다() {
		LearningSessionQuestion currentQuestion = createSessionQuestion();
		LearningSessionQuestion otherQuestion = createSessionQuestion();
		SpeechAnswer speechAnswer = completedSpeechAnswer(
			otherQuestion,
			"The boy is running."
		);

		assertThrows(
			IllegalArgumentException.class,
			() -> Answer.create(
				currentQuestion,
				speechAnswer,
				1,
				AnswerEvaluation.from(AnswerResult.CORRECT),
				FEEDBACK_TEXT
			)
		);
	}

	@Test
	void 답변_시도_번호는_1_이상이어야_한다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		assertThrows(
			IllegalArgumentException.class,
			() -> Answer.create(
				sessionQuestion,
				completedSpeechAnswer(
					sessionQuestion,
					"The boy is running."
				),
				0,
				AnswerEvaluation.from(AnswerResult.CORRECT),
				FEEDBACK_TEXT
			)
		);
	}

	@Test
	void 채점_결과_없이_답변을_생성할_수_없다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		assertThrows(
			IllegalArgumentException.class,
			() -> Answer.create(
				sessionQuestion,
				completedSpeechAnswer(
					sessionQuestion,
					"The boy is running."
				),
				1,
				null,
				FEEDBACK_TEXT
			)
		);
	}

	@Test
	void 음성_답변_없이_답변을_생성할_수_없다() {
		assertThrows(
			IllegalArgumentException.class,
			() -> Answer.create(
				createSessionQuestion(),
				null,
				1,
				AnswerEvaluation.from(AnswerResult.CORRECT),
				FEEDBACK_TEXT
			)
		);
	}

	@Test
	void 피드백_없이_답변을_생성할_수_없다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();

		assertThrows(
			IllegalArgumentException.class,
			() -> Answer.create(
				sessionQuestion,
				completedSpeechAnswer(
					sessionQuestion,
					"The boy is running."
				),
				1,
				AnswerEvaluation.from(AnswerResult.CORRECT),
				" "
			)
		);
	}

	@Test
	void 피드백은_300자를_초과할_수_없다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();

		assertThrows(
			IllegalArgumentException.class,
			() -> Answer.create(
				sessionQuestion,
				completedSpeechAnswer(
					sessionQuestion,
					"The boy is running."
				),
				1,
				AnswerEvaluation.from(AnswerResult.CORRECT),
				"가".repeat(301)
			)
		);
	}

	private SpeechAnswer completedSpeechAnswer(
		LearningSessionQuestion sessionQuestion,
		String transcript
	) {
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			sessionQuestion,
			"request-key",
			1
		);
		speechAnswer.complete(transcript, 0.94, "TEST_STT");
		return speechAnswer;
	}

	private LearningSessionQuestion createSessionQuestion() {
		Question question = Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"남자아이는 무엇을 하고 있나요?",
			null,
			"",
			"The boy is running.",
			Set.of("He is running.", "He's running."),
			"He is ____ing.",
			null
		);
		LearningSession session = LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(question)
		);

		return session.getCurrentQuestion();
	}
}
