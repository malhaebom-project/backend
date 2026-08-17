package com.malhaebom.malhaebom.integration.learning;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.malhaebom.malhaebom.domain.learning.AnswerSubmission;
import com.malhaebom.malhaebom.domain.learning.AnswerSubmissionStatus;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerRepository;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerSubmissionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.service.AnswerAssessmentService;
import com.malhaebom.malhaebom.service.AnswerSubmissionTransactionService;
import com.malhaebom.malhaebom.service.LearningAnswerService;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;
import com.malhaebom.malhaebom.service.port.AnswerAssessmentGenerator;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
	JpaAuditingConfiguration.class,
	LearningAnswerService.class,
	AnswerSubmissionTransactionService.class,
	AnswerAssessmentService.class,
	LearningAnswerTransactionBoundaryJpaTest.AssessmentTestConfiguration.class
})
class LearningAnswerTransactionBoundaryJpaTest {

	@Autowired
	private LearningSessionRepository learningSessionRepository;
	@Autowired
	private QuestionRepository questionRepository;
	@Autowired
	private SpeechAnswerRepository speechAnswerRepository;
	@Autowired
	private AnswerRepository answerRepository;
	@Autowired
	private AnswerSubmissionRepository answerSubmissionRepository;
	@Autowired
	private LearningAnswerService learningAnswerService;
	@Autowired
	private TestAnswerAssessmentGenerator assessmentGenerator;
	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUp() {
		assessmentGenerator.reset();
	}

	@Test
	void 채점은_예약과_완료_트랜잭션_사이에서_실행되고_실패_예약을_재사용한다() {
		LearningSession session = LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			"He is ____ing."
		);
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			question,
			"transaction-boundary-request",
			1
		);
		speechAnswer.complete("He is running.", 0.94, "TEST_STT");
		speechAnswerRepository.saveAndFlush(speechAnswer);
		assessmentGenerator.willThrow(
			new IllegalStateException("OpenAI timeout")
		);

		assertApiException(
			ErrorCode.ANSWER_ASSESSMENT_FAILED,
			() -> learningAnswerService.submit(
				session.getId(),
				question.getId(),
				speechAnswer.getId()
			)
		);
		AnswerSubmission failed = answerSubmissionRepository
			.findBySpeechAnswer_Id(speechAnswer.getId())
			.orElseThrow();
		assertEquals(AnswerSubmissionStatus.FAILED, failed.getStatus());
		assertEquals(0, answerRepository.count());

		assessmentGenerator.willReturn(new AnswerAssessment(
			true,
			50,
			30,
			20,
			"현재진행형을 정확하게 사용했어요!"
		));
		AnswerSubmissionResult completed = learningAnswerService.submit(
			session.getId(),
			question.getId(),
			speechAnswer.getId()
		);
		AnswerSubmissionResult duplicated = learningAnswerService.submit(
			session.getId(),
			question.getId(),
			speechAnswer.getId()
		);

		AnswerSubmission saved = answerSubmissionRepository
			.findBySpeechAnswer_Id(speechAnswer.getId())
			.orElseThrow();
		assertEquals(failed.getId(), saved.getId());
		assertEquals(AnswerSubmissionStatus.COMPLETED, saved.getStatus());
		assertEquals(completed.answerId(), duplicated.answerId());
		assertEquals(2, assessmentGenerator.transactionStates.size());
		assertTrue(assessmentGenerator.transactionStates.stream().noneMatch(
			Boolean::booleanValue
		));
		assertFalse(TransactionSynchronizationManager
			.isActualTransactionActive());
	}

	@Test
	void 상위_트랜잭션에서_호출해도_채점_구간은_트랜잭션을_중단한다() {
		LearningSession session = LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			"He is ____ing."
		);
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			question,
			"outer-transaction-request",
			1
		);
		speechAnswer.complete("He is running.", 0.94, "TEST_STT");
		speechAnswerRepository.saveAndFlush(speechAnswer);
		assessmentGenerator.willReturn(new AnswerAssessment(
			true,
			50,
			30,
			20,
			"현재진행형을 정확하게 사용했어요!"
		));

		TransactionTemplate transactionTemplate = new TransactionTemplate(
			transactionManager
		);
		AnswerSubmissionResult result = transactionTemplate.execute(status -> {
			assertTrue(TransactionSynchronizationManager
				.isActualTransactionActive());
			AnswerSubmissionResult submitted = learningAnswerService.submit(
				session.getId(),
				question.getId(),
				speechAnswer.getId()
			);
			assertTrue(TransactionSynchronizationManager
				.isActualTransactionActive());
			return submitted;
		});

		assertNotNull(result);
		assertNotNull(result.answerId());
		assertEquals(question.getId(), result.sessionQuestionId());
		assertEquals("He is running.", result.answerText());
		assertEquals(
			"현재진행형을 정확하게 사용했어요!",
			result.feedbackText()
		);
		assertEquals(List.of(false), assessmentGenerator.transactionStates);
		assertFalse(TransactionSynchronizationManager
			.isActualTransactionActive());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class AssessmentTestConfiguration {

		@Bean
		TestAnswerAssessmentGenerator answerAssessmentGenerator() {
			return new TestAnswerAssessmentGenerator();
		}
	}

	private static final class TestAnswerAssessmentGenerator
		implements AnswerAssessmentGenerator {

		private final List<Boolean> transactionStates = new ArrayList<>();
		private AnswerAssessment assessment;
		private RuntimeException exception;

		void reset() {
			transactionStates.clear();
			assessment = null;
			exception = null;
		}

		void willReturn(AnswerAssessment assessment) {
			this.assessment = assessment;
			exception = null;
		}

		void willThrow(RuntimeException exception) {
			this.exception = exception;
			assessment = null;
		}

		@Override
		public AnswerAssessment generate(AnswerAssessmentInput input) {
			transactionStates.add(TransactionSynchronizationManager
				.isActualTransactionActive());
			if (exception != null) {
				throw exception;
			}
			return assessment;
		}
	}
}
