package com.malhaebom.malhaebom.integration.learning;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

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
import com.malhaebom.malhaebom.infra.observability.MicrometerAnswerSubmissionMetricsRecorder;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.service.AnswerSubmissionTransactionService;
import com.malhaebom.malhaebom.service.ChildProfileService;
import com.malhaebom.malhaebom.service.LearningAnswerService;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionTask;
import com.malhaebom.malhaebom.service.port.AnswerAssessmentGenerator;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = "malhaebom.answer-submission.processing-timeout=1s")
@Import({
	JpaAuditingConfiguration.class,
	LearningAnswerService.class,
	MicrometerAnswerSubmissionMetricsRecorder.class,
	AnswerSubmissionTransactionService.class,
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
	private TestClock clock;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@MockitoBean
	private ChildProfileService childProfileService;

	@BeforeEach
	void setUp() {
		assessmentGenerator.reset();
		clock.reset();
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
			() -> submit(
				session.getId(),
				question.getId(),
				speechAnswer.getId()
			)
		);
		AnswerSubmission failed = answerSubmissionRepository
			.findBySpeechAnswer_Id(speechAnswer.getId())
			.orElseThrow();
		assertEquals(AnswerSubmissionStatus.FAILED, failed.getStatus());
		assertFalse(answerRepository.existsBySpeechAnswer_Id(
			speechAnswer.getId()
		));

		assessmentGenerator.willReturn(new AnswerAssessment(
			true,
			50,
			30,
			20,
			"현재진행형을 정확하게 사용했어요!"
		));
		AnswerSubmissionResult completed = submit(
			session.getId(),
			question.getId(),
			speechAnswer.getId()
		);
		AnswerSubmissionResult duplicated = submit(
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
	void 작업_기한_이후의_채점_결과는_저장하지_않고_동일_제출을_재시도한다() {
		LearningSession session = LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			"He is ____ing."
		);
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			question,
			"expired-deadline-request",
			1
		);
		speechAnswer.complete("He is running.", 0.94, "TEST_STT");
		speechAnswerRepository.saveAndFlush(speechAnswer);
		AnswerAssessment assessment = new AnswerAssessment(
			true,
			50,
			30,
			20,
			"현재진행형을 정확하게 사용했어요!"
		);
		assessmentGenerator.willReturnThen(
			assessment,
			() -> clock.advanceAfterChecks(
				1,
				Duration.ofSeconds(25)
			)
		);

		assertApiException(
			ErrorCode.ANSWER_SUBMISSION_TIMEOUT,
			() -> submit(
				session.getId(),
				question.getId(),
				speechAnswer.getId()
			)
		);
		AnswerSubmission failed = answerSubmissionRepository
			.findBySpeechAnswer_Id(speechAnswer.getId())
			.orElseThrow();

		assertEquals(AnswerSubmissionStatus.FAILED, failed.getStatus());
		assertFalse(answerRepository.existsBySpeechAnswer_Id(
			speechAnswer.getId()
		));
		assertTrue(isSessionInProgress(session.getId()));

		assessmentGenerator.willThrow(
			new IllegalStateException("재시도 채점 실패")
		);
		assertApiException(
			ErrorCode.ANSWER_ASSESSMENT_FAILED,
			() -> submit(
				session.getId(),
				question.getId(),
				speechAnswer.getId()
			)
		);
		AnswerSubmission retried = answerSubmissionRepository
			.findById(failed.getId())
			.orElseThrow();

		assertEquals(AnswerSubmissionStatus.FAILED, retried.getStatus());
		assertEquals("재시도 채점 실패", retried.getFailureMessage());
		assertFalse(answerRepository.existsBySpeechAnswer_Id(
			speechAnswer.getId()
		));
	}

	@Test
	void 작업_기한이_만료되면_채점을_취소하고_예약을_실패시킨다() {
		LearningSession session = LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			"He is ____ing."
		);
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			question,
			"timeout-cancellation-request",
			1
		);
		speechAnswer.complete("He is running.", 0.94, "TEST_STT");
		speechAnswerRepository.saveAndFlush(speechAnswer);
		CompletableFuture<AnswerAssessment> assessment =
			new CompletableFuture<>();
		assessmentGenerator.willReturn(assessment);

		CompletionStage<AnswerSubmissionResult> submission =
			learningAnswerService.submitAsync(LearningJpaTestFixture.USER_ID,
				session.getId(),
				question.getId(),
				speechAnswer.getId()
			).result();
		assertFalse(submission.toCompletableFuture().isDone());

		assertApiException(
			ErrorCode.ANSWER_SUBMISSION_TIMEOUT,
			() -> await(submission)
		);

		AnswerSubmission failed = answerSubmissionRepository
			.findBySpeechAnswer_Id(speechAnswer.getId())
			.orElseThrow();
		assertTrue(assessment.isCancelled());
		assertEquals(1, assessmentGenerator.cancellationCount);
		assertEquals(AnswerSubmissionStatus.FAILED, failed.getStatus());
		assertEquals(
			ErrorCode.ANSWER_SUBMISSION_TIMEOUT.getMessage(),
			failed.getFailureMessage()
		);
		assertFalse(answerRepository.existsBySpeechAnswer_Id(
			speechAnswer.getId()
		));
	}

	@Test
	void Servlet_타임아웃으로_제출을_취소하면_늦은_결과를_막고_재시도한다() {
		LearningSession session = LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			"He is ____ing."
		);
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			question,
			"servlet-timeout-request",
			1
		);
		speechAnswer.complete("He is running.", 0.94, "TEST_STT");
		speechAnswerRepository.saveAndFlush(speechAnswer);
		CompletableFuture<AnswerAssessment> assessment =
			new CompletableFuture<>();
		assessmentGenerator.willReturn(assessment);
		AnswerSubmissionTask submission = learningAnswerService.submitAsync(LearningJpaTestFixture.USER_ID,
			session.getId(),
			question.getId(),
			speechAnswer.getId()
		);

		assertTrue(submission.cancel());
		assertApiException(
			ErrorCode.ANSWER_SUBMISSION_TIMEOUT,
			() -> await(submission.result())
		);
		AnswerSubmission failed = answerSubmissionRepository
			.findBySpeechAnswer_Id(speechAnswer.getId())
			.orElseThrow();
		assertEquals(AnswerSubmissionStatus.FAILED, failed.getStatus());
		assertEquals(
			ErrorCode.ANSWER_SUBMISSION_TIMEOUT.getMessage(),
			failed.getFailureMessage()
		);
		assertTrue(assessment.isCancelled());
		assertFalse(assessment.complete(new AnswerAssessment(
			true,
			50,
			30,
			20,
			"늦게 도착한 결과"
		)));
		assertFalse(answerRepository.existsBySpeechAnswer_Id(
			speechAnswer.getId()
		));
		assertTrue(isSessionInProgress(session.getId()));

		assessmentGenerator.willReturn(new AnswerAssessment(
			true,
			50,
			30,
			20,
			"재시도 채점 성공"
		));
		AnswerSubmissionResult retried = submit(
			session.getId(),
			question.getId(),
			speechAnswer.getId()
		);
		AnswerSubmission completed = answerSubmissionRepository
			.findById(failed.getId())
			.orElseThrow();

		assertEquals(AnswerSubmissionStatus.COMPLETED, completed.getStatus());
		assertEquals(retried.answerId(), completed.getAnswer().getId());
		assertEquals(1, answerRepository.count());
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
			AnswerSubmissionResult submitted = submit(
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

	private boolean isSessionInProgress(Long sessionId) {
		TransactionTemplate transactionTemplate = new TransactionTemplate(
			transactionManager
		);
		return Boolean.TRUE.equals(transactionTemplate.execute(status ->
			learningSessionRepository.findById(sessionId)
				.orElseThrow()
				.isInProgress()
		));
	}

	private AnswerSubmissionResult submit(
		Long sessionId,
		Long sessionQuestionId,
		Long speechAnswerId
	) {
		return await(learningAnswerService.submitAsync(LearningJpaTestFixture.USER_ID,
			sessionId,
			sessionQuestionId,
			speechAnswerId
		).result());
	}

	private <T> T await(CompletionStage<T> stage) {
		try {
			return stage.toCompletableFuture().join();
		} catch (CompletionException exception) {
			if (exception.getCause() instanceof RuntimeException cause) {
				throw cause;
			}
			throw exception;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class AssessmentTestConfiguration {

		@Bean
		SimpleMeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

		@Bean
		TestAnswerAssessmentGenerator answerAssessmentGenerator() {
			return new TestAnswerAssessmentGenerator();
		}

		@Bean
		TestClock clock() {
			return new TestClock();
		}
	}

	private static final class TestAnswerAssessmentGenerator implements AnswerAssessmentGenerator {
		private final List<Boolean> transactionStates = new ArrayList<>();
		private AnswerAssessment assessment;
		private RuntimeException exception;
		private Runnable afterGenerate;
		private CompletionStage<AnswerAssessment> stage;
		private int cancellationCount;

		void reset() {
			transactionStates.clear();
			assessment = null;
			exception = null;
			stage = null;
			cancellationCount = 0;
			afterGenerate = () -> {
			};
		}

		void willReturn(AnswerAssessment assessment) {
			this.assessment = assessment;
			exception = null;
			stage = null;
			afterGenerate = () -> {
			};
		}

		void willReturn(CompletionStage<AnswerAssessment> stage) {
			this.stage = stage;
			assessment = null;
			exception = null;
			afterGenerate = () -> {
			};
		}

		void willReturnThen(AnswerAssessment assessment, Runnable afterGenerate) {
			this.assessment = assessment;
			exception = null;
			stage = null;
			this.afterGenerate = afterGenerate;
		}

		void willThrow(RuntimeException exception) {
			this.exception = exception;
			assessment = null;
			stage = null;
			afterGenerate = () -> {
			};
		}

		@Override
		public AnswerAssessmentTask generateAsync(
			AnswerAssessmentInput input
		) {
			transactionStates.add(TransactionSynchronizationManager
				.isActualTransactionActive());
			if (exception != null) {
				stage = CompletableFuture.failedFuture(exception);
			} else if (stage == null) {
				afterGenerate.run();
				stage = CompletableFuture.completedFuture(assessment);
			}
			CompletionStage<AnswerAssessment> taskStage = stage;
			return new AnswerAssessmentTask(taskStage, () -> {
				cancellationCount++;
				return taskStage.toCompletableFuture().cancel(true);
			});
		}
	}

	private static final class TestClock extends Clock {
		private Instant current;
		private final ZoneId zone;
		private int checksBeforeAdvance = -1;
		private Duration pendingAdvance = Duration.ZERO;

		private TestClock() {
			this(Instant.now(), ZoneOffset.UTC);
		}

		private TestClock(Instant current, ZoneId zone) {
			this.current = current;
			this.zone = zone;
		}

		void reset() {
			current = Instant.now();
			checksBeforeAdvance = -1;
			pendingAdvance = Duration.ZERO;
		}

		void advanceAfterChecks(int checks, Duration duration) {
			checksBeforeAdvance = checks;
			pendingAdvance = duration;
		}

		@Override
		public ZoneId getZone() {
			return zone;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return new TestClock(current, zone);
		}

		@Override
		public Instant instant() {
			if (checksBeforeAdvance == 0) {
				current = current.plus(pendingAdvance);
				checksBeforeAdvance = -1;
				pendingAdvance = Duration.ZERO;
			} else if (checksBeforeAdvance > 0) {
				checksBeforeAdvance--;
			}
			return current;
		}
	}
}
