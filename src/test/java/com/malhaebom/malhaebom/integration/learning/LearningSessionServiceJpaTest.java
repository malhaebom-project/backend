package com.malhaebom.malhaebom.integration.learning;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.service.LearningSessionService;

@DataJpaTest
@Import({LearningSessionService.class, JpaAuditingConfiguration.class})
class LearningSessionServiceJpaTest {

	@Autowired
	private LearningSessionService learningSessionService;
	@Autowired
	private LearningSessionRepository learningSessionRepository;
	@Autowired
	private QuestionRepository questionRepository;

	@Test
	void 존재하지_않는_학습_주제로_세션을_만들_수_없다() {
		assertApiException(
			ErrorCode.LEARNING_TOPIC_NOT_FOUND,
			() -> learningSessionService.create(
				1L,
				999L,
				Difficulty.EASY,
				List.of(QuestionType.PICTURE_DESCRIPTION),
				1
			)
		);
	}

	@Test
	void 문제_수가_부족하면_세션을_만들_수_없다() {
		assertApiException(
			ErrorCode.INSUFFICIENT_QUESTIONS,
			() -> learningSessionService.create(
				1L,
				3L,
				Difficulty.EASY,
				List.of(QuestionType.PICTURE_DESCRIPTION),
				1
			)
		);
	}

	@Test
	void 완료된_세션에서는_다음_문제를_조회할_수_없다() {
		LearningSession session = LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			null
		);
		session.complete();

		assertApiException(
			ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS,
			() -> learningSessionService.getNextQuestion(session.getId())
		);
	}

	@Test
	void 완료되지_않은_세션은_완료할_수_없다() {
		LearningSession session = LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			null
		);

		assertApiException(
			ErrorCode.INVALID_REQUEST,
			() -> learningSessionService.complete(session.getId())
		);
	}
}
