package com.malhaebom.malhaebom.integration.learning;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
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
	void 완료되지_않은_세션은_완료할_수_없다() {
		LearningSession session = LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			null
		);

		assertThrows(
			IllegalStateException.class,
			() -> learningSessionService.complete(session.getId())
		);
	}
}
