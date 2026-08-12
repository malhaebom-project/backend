package com.malhaebom.malhaebom.domain.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LearningSessionTest {

	@Test
	void 학습_세션을_생성하면_첫_번째_문제가_현재_문제가_된다() {
		List<Question> questions = createQuestions();
		LearningSession session = createSession(questions);

		assertEquals(LearningSessionStatus.IN_PROGRESS, session.getStatus());
		assertEquals(3, session.getQuestionCount());
		assertEquals(0, session.getCurrentQuestionIndex());
		assertSame(questions.get(0), session.getCurrentQuestion().getQuestion());
	}

	@Test
	void 현재_문제를_완료하면_다음_문제로_이동한다() {
		List<Question> questions = createQuestions();
		LearningSession session = createSession(questions);

		session.completeCurrentQuestion(true);

		assertEquals(1, session.getCurrentQuestionIndex());
		assertSame(questions.get(1), session.getCurrentQuestion().getQuestion());
		assertEquals(1, session.getCorrectCount());
	}

	@Test
	void 오답_시도를_기록하면_현재_문제를_유지한다() {
		LearningSession session = createSession(createQuestions());
		LearningSessionQuestion currentQuestion = session.getCurrentQuestion();

		session.recordWrongAnswerAttempt();

		assertEquals(0, session.getCurrentQuestionIndex());
		assertSame(currentQuestion, session.getCurrentQuestion());
		assertEquals(1, currentQuestion.getWrongAnswerCount());
		assertFalse(currentQuestion.isCompleted());
	}

	@Test
	void 오답으로_완료하면_개별_문제의_오답_횟수가_증가한다() {
		LearningSession session = createSession(createQuestions());
		LearningSessionQuestion currentQuestion = session.getCurrentQuestion();

		session.completeCurrentQuestion(false);

		assertEquals(1, currentQuestion.getWrongAnswerCount());
		assertEquals(0, session.getCorrectCount());
	}

	@Test
	void 오답_후_재시도를_건너뛰면_오답_횟수를_늘리지_않고_다음_문제로_이동한다() {
		List<Question> questions = createQuestions();
		LearningSession session = createSession(questions);
		LearningSessionQuestion skippedQuestion = session.getCurrentQuestion();
		session.recordWrongAnswerAttempt();

		session.skipRetryOnCurrentQuestion();

		assertTrue(skippedQuestion.isCompleted());
		assertFalse(skippedQuestion.isCorrect());
		assertEquals(1, skippedQuestion.getWrongAnswerCount());
		assertEquals(1, session.getCurrentQuestionIndex());
		assertSame(questions.get(1), session.getCurrentQuestion().getQuestion());
	}

	@Test
	void 마지막_문제의_재시도를_건너뛰면_세션이_완료된다() {
		LearningSession session = LearningSession.create(
			1L,
			LearningTopic.ANIMAL,
			Difficulty.EASY,
			List.of(createQuestions().get(0))
		);
		session.recordWrongAnswerAttempt();

		session.skipRetryOnCurrentQuestion();

		assertTrue(session.isCompleted());
		assertEquals(1, session.getCurrentQuestionIndex());
		assertNotNull(session.getCompletedAt());
	}

	@Test
	void 현재_문제에서_힌트를_사용할_수_있다() {
		LearningSession session = createSession(createQuestions());

		session.useHintOnCurrentQuestion();

		assertEquals(1, session.getCurrentQuestion().getHintUsedCount());
	}

	@Test
	void 마지막_문제를_완료하면_세션이_완료된다() {
		LearningSession session = createSession(createQuestions());

		session.completeCurrentQuestion(true);
		session.completeCurrentQuestion(false);
		session.completeCurrentQuestion(true);

		assertEquals(LearningSessionStatus.COMPLETED, session.getStatus());
		assertEquals(3, session.getCurrentQuestionIndex());
		assertEquals(2, session.getCorrectCount());
		assertNotNull(session.getCompletedAt());
		assertTrue(session.isCompleted());
	}

	private LearningSession createSession(List<Question> questions) {
		return LearningSession.create(
			1L,
			LearningTopic.ANIMAL,
			Difficulty.EASY,
			questions
		);
	}

	private List<Question> createQuestions() {
		return List.of(
			createQuestion("What is this?", "이것은 무엇인가요?"),
			createQuestion("What is he doing?", "그는 무엇을 하고 있나요?"),
			createQuestion("Tell me about it.", "이것에 대해 말해 보세요.")
		);
	}

	private Question createQuestion(String questionText, String questionTextKo) {
		return Question.create(
			LearningTopic.ANIMAL,
			Difficulty.EASY,
			QuestionType.SHORT_ANSWER,
			questionText,
			questionTextKo,
			null,
			"answer",
			Set.of("accepted answer"),
			null,
			null
		);
	}
}
