package com.malhaebom.malhaebom.integration.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.global.exception.CurrentQuestionMismatchException;
import com.malhaebom.malhaebom.global.exception.LearningSessionNotFoundException;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.service.LearningHintService;

@DataJpaTest
@Import({LearningHintService.class, JpaAuditingConfiguration.class})
class LearningHintServiceJpaTest {

	@Autowired
	private LearningHintService learningHintService;
	@Autowired
	private LearningSessionRepository learningSessionRepository;
	@Autowired
	private QuestionRepository questionRepository;

	@Test
	void 현재_문제의_힌트를_반환하고_사용_횟수를_저장한다() {
		LearningSession session = saveSession("He is ____ing.");
		Question question = session.getCurrentQuestion().getQuestion();

		Question result = learningHintService.request(
			session.getId(),
			question.getId()
		);

		assertEquals(question.getId(), result.getId());
		assertEquals(1, session.getCurrentQuestion().getHintUsedCount());
	}

	@Test
	void 현재_문제가_아니면_힌트_사용_횟수를_변경하지_않는다() {
		LearningSession session = saveSession("He is ____ing.");

		assertThrows(
			CurrentQuestionMismatchException.class,
			() -> learningHintService.request(session.getId(), 999L)
		);
		assertEquals(0, session.getCurrentQuestion().getHintUsedCount());
	}

	@Test
	void 등록된_힌트가_없으면_사용_횟수를_변경하지_않는다() {
		LearningSession session = saveSession(null);
		Long questionId = session.getCurrentQuestion().getQuestion().getId();

		assertThrows(
			IllegalStateException.class,
			() -> learningHintService.request(session.getId(), questionId)
		);
		assertEquals(0, session.getCurrentQuestion().getHintUsedCount());
	}

	@Test
	void 존재하지_않는_세션은_전용_예외로_거부한다() {
		assertThrows(
			LearningSessionNotFoundException.class,
			() -> learningHintService.request(999L, 999L)
		);
	}

	private LearningSession saveSession(String hintText) {
		return LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			hintText
		);
	}
}
