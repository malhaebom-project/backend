package com.malhaebom.malhaebom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.global.exception.CurrentQuestionMismatchException;
import com.malhaebom.malhaebom.global.exception.LearningSessionNotFoundException;

@ExtendWith(MockitoExtension.class)
class LearningHintServiceTest {

	private static final Long SESSION_ID = 10L;
	private static final Long QUESTION_ID = 20L;
	private static final String HINT_TEXT = "It is a ___.";

	@Mock
	private LearningSessionRepository learningSessionRepository;

	private LearningHintService learningHintService;
	private LearningSession session;
	private Question question;

	@BeforeEach
	void setUp() {
		learningHintService = new LearningHintService(
			learningSessionRepository
		);
		question = createQuestion(HINT_TEXT);
		ReflectionTestUtils.setField(question, "id", QUESTION_ID);
		session = LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(question)
		);
	}

	@Test
	void 현재_문제의_힌트를_반환하고_사용_횟수를_증가시킨다() {
		when(learningSessionRepository.findForUpdateById(SESSION_ID))
			.thenReturn(Optional.of(session));

		Question result = learningHintService.request(
			SESSION_ID,
			QUESTION_ID
		);

		assertSame(question, result);
		assertEquals(
			1,
			session.getCurrentQuestion().getHintUsedCount()
		);
		verify(learningSessionRepository).findForUpdateById(SESSION_ID);
	}

	@Test
	void 현재_문제가_아닌_문제의_힌트는_요청할_수_없다() {
		when(learningSessionRepository.findForUpdateById(SESSION_ID))
			.thenReturn(Optional.of(session));

		assertThrows(
			CurrentQuestionMismatchException.class,
			() -> learningHintService.request(SESSION_ID, 999L)
		);
		assertEquals(
			0,
			session.getCurrentQuestion().getHintUsedCount()
		);
	}

	@Test
	void 등록된_힌트가_없으면_사용_횟수를_증가시키지_않는다() {
		Question questionWithoutHint = createQuestion(null);
		ReflectionTestUtils.setField(
			questionWithoutHint,
			"id",
			QUESTION_ID
		);
		LearningSession sessionWithoutHint = LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(questionWithoutHint)
		);
		when(learningSessionRepository.findForUpdateById(SESSION_ID))
			.thenReturn(Optional.of(sessionWithoutHint));

		assertThrows(
			IllegalStateException.class,
			() -> learningHintService.request(SESSION_ID, QUESTION_ID)
		);
		assertEquals(
			0,
			sessionWithoutHint.getCurrentQuestion().getHintUsedCount()
		);
	}

	@Test
	void 존재하지_않는_세션의_힌트는_요청할_수_없다() {
		when(learningSessionRepository.findForUpdateById(SESSION_ID))
			.thenReturn(Optional.empty());

		assertThrows(
			LearningSessionNotFoundException.class,
			() -> learningHintService.request(SESSION_ID, QUESTION_ID)
		);
	}

	private Question createQuestion(String hintText) {
		return Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.SHORT_ANSWER,
			"What is this?",
			"이것은 무엇인가요?",
			null,
			"It is a book.",
			Set.of("It is a book."),
			hintText,
			null
		);
	}
}
