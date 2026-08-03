package com.malhaebom.malhaebom.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;

@ExtendWith(MockitoExtension.class)
class LearningSessionServiceTest {

	private static final Long SESSION_ID = 10L;

	@Mock
	private LearningSessionRepository learningSessionRepository;

	@Mock
	private QuestionRepository questionRepository;

	private LearningSessionService learningSessionService;

	@BeforeEach
	void setUp() {
		learningSessionService = new LearningSessionService(
			learningSessionRepository,
			questionRepository
		);
	}

	@Test
	void 이미_완료된_세션이면_현재_완료_결과를_반환한다() {
		LearningSession session = createSession();
		session.completeCurrentQuestion(true);
		when(learningSessionRepository.findWithQuestionsById(SESSION_ID))
			.thenReturn(Optional.of(session));

		LearningSession result = learningSessionService.complete(SESSION_ID);

		assertSame(session, result);
	}

	@Test
	void 모든_문제를_완료하지_않은_세션은_완료할_수_없다() {
		LearningSession session = createSession();
		when(learningSessionRepository.findWithQuestionsById(SESSION_ID))
			.thenReturn(Optional.of(session));

		assertThrows(
			IllegalStateException.class,
			() -> learningSessionService.complete(SESSION_ID)
		);
	}

	private LearningSession createSession() {
		Question question = Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.SHORT_ANSWER,
			"What is this?",
			"이것은 무엇인가요?",
			null,
			"It is a book.",
			Set.of("It is a book."),
			null,
			null
		);
		return LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(question)
		);
	}
}
