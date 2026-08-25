package com.malhaebom.malhaebom.integration.learning;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.service.LearningHintService;
import com.malhaebom.malhaebom.service.ChildProfileService;

@DataJpaTest
@Import({LearningHintService.class, JpaAuditingConfiguration.class})
class LearningHintServiceJpaTest {

	@Autowired
	private LearningHintService learningHintService;
	@Autowired
	private LearningSessionRepository learningSessionRepository;
	@Autowired
	private QuestionRepository questionRepository;
	@MockitoBean
	private ChildProfileService childProfileService;

	@Test
	void 현재_문제의_힌트를_반환하고_사용_횟수를_저장한다() {
		LearningSession session = saveSession("He is ____ing.");
		Question question = session.getCurrentQuestion().getQuestion();

		Question result = learningHintService.request(LearningJpaTestFixture.USER_ID,
			session.getId(),
			question.getId()
		);

		assertEquals(question.getId(), result.getId());
		assertEquals(1, session.getCurrentQuestion().getHintUsedCount());
	}

	@Test
	void 현재_문제가_아니면_힌트_사용_횟수를_변경하지_않는다() {
		LearningSession session = saveSession("He is ____ing.");

		assertApiException(
			ErrorCode.CURRENT_QUESTION_MISMATCH,
			() -> learningHintService.request(LearningJpaTestFixture.USER_ID, session.getId(), 999L)
		);
		assertEquals(0, session.getCurrentQuestion().getHintUsedCount());
	}

	@Test
	void 등록된_힌트가_없으면_사용_횟수를_변경하지_않는다() {
		LearningSession session = saveSession(null);
		Long questionId = session.getCurrentQuestion().getQuestion().getId();

		assertApiException(
			ErrorCode.INVALID_REQUEST,
			() -> learningHintService.request(LearningJpaTestFixture.USER_ID, session.getId(), questionId)
		);
		assertEquals(0, session.getCurrentQuestion().getHintUsedCount());
	}

	@Test
	void 존재하지_않는_세션은_전용_예외로_거부한다() {
		assertApiException(
			ErrorCode.LEARNING_SESSION_NOT_FOUND,
			() -> learningHintService.request(LearningJpaTestFixture.USER_ID, 999L, 999L)
		);
	}

	@Test
	void 완료된_세션에서는_힌트를_요청할_수_없다() {
		LearningSession session = saveSession("He is ____ing.");
		Long questionId = session.getCurrentQuestion().getQuestion().getId();
		session.complete();

		assertApiException(
			ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS,
			() -> learningHintService.request(LearningJpaTestFixture.USER_ID, session.getId(), questionId)
		);
	}

	@Test
	void 다른_사용자는_힌트를_사용할_수_없다() {
		LearningSession session = saveSession("현재 문제의 힌트");
		Long questionId = session.getCurrentQuestion().getQuestion().getId();
		Long otherUserId = 999L;
		doThrow(new ApiException(ErrorCode.CHILD_ACCESS_DENIED))
			.when(childProfileService).getOwnedActive(otherUserId, session.getChildId());

		assertApiException(ErrorCode.CHILD_ACCESS_DENIED,
			() -> learningHintService.request(otherUserId, session.getId(), questionId));

		assertEquals(0, session.getCurrentQuestion().getHintUsedCount());
	}

	private LearningSession saveSession(String hintText) {
		return LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			hintText
		);
	}
}
