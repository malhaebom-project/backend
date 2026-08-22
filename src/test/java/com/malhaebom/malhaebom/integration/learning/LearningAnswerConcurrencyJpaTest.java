package com.malhaebom.malhaebom.integration.learning;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
import com.malhaebom.malhaebom.infra.ai.AnswerAssessmentConcurrencyLimiter;
import com.malhaebom.malhaebom.infra.ai.AnswerAssessmentConcurrencyProperties;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.infra.time.TimeConfiguration;
import com.malhaebom.malhaebom.service.AnswerAssessmentService;
import com.malhaebom.malhaebom.service.AnswerSubmissionTransactionService;
import com.malhaebom.malhaebom.service.LearningAnswerService;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionPreparation.Processing;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;
import com.malhaebom.malhaebom.service.port.AnswerAssessmentGenerator;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
	JpaAuditingConfiguration.class,
	LearningAnswerService.class,
	AnswerSubmissionTransactionService.class,
	AnswerAssessmentService.class,
	TimeConfiguration.class,
	LearningAnswerConcurrencyJpaTest.AssessmentTestConfiguration.class
})
class LearningAnswerConcurrencyJpaTest {

	private static final AnswerAssessment CORRECT_ASSESSMENT =
		new AnswerAssessment(
			true,
			50,
			30,
			20,
			"현재진행형을 정확하게 사용했어요!"
		);

	@Autowired
	private LearningSessionRepository learningSessionRepository;
	@Autowired
	private QuestionRepository questionRepository;
	@Autowired
	private SpeechAnswerRepository speechAnswerRepository;
	@Autowired
	private AnswerSubmissionRepository answerSubmissionRepository;
	@Autowired
	private AnswerRepository answerRepository;
	@Autowired
	private LearningAnswerService learningAnswerService;
	@Autowired
	private AnswerSubmissionTransactionService submissionTransactionService;
	@Autowired
	private BlockingAnswerAssessmentGenerator assessmentGenerator;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		assessmentGenerator.reset();
	}

	@AfterEach
	void tearDown() {
		assessmentGenerator.releaseAllAssessments();
	}

	@Test
	void 같은_음성_답변의_동시_제출은_한_요청만_채점한다() throws Exception {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(question, 1);
		CompletionStage<AnswerSubmissionResult> firstRequest =
			learningAnswerService.submitAsync(
				session.getId(),
				question.getId(),
				speechAnswer.getId()
			).result();

		try {
			assertTrue(assessmentGenerator.awaitAssessmentStarted());
			assertFalse(firstRequest.toCompletableFuture().isDone());

			assertApiException(
				ErrorCode.ANSWER_SUBMISSION_PROCESSING,
				() -> await(learningAnswerService.submitAsync(
					session.getId(),
					question.getId(),
					speechAnswer.getId()
				).result())
			);

			assessmentGenerator.releaseAssessment();
			AnswerSubmissionResult completed = await(firstRequest);
			AnswerSubmission submission = answerSubmissionRepository
				.findBySpeechAnswer_Id(speechAnswer.getId())
				.orElseThrow();

			assertEquals(1, assessmentGenerator.callCount());
			assertEquals(AnswerSubmissionStatus.COMPLETED, submission.getStatus());
			assertEquals(completed.answerId(), submission.getAnswer().getId());
		} finally {
			assessmentGenerator.releaseAssessment();
		}
	}

	@Test
	void 같은_문제의_다른_음성_답변도_채점_중에는_제출할_수_없다()
		throws Exception {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer firstSpeech = saveCompletedSpeechAnswer(question, 1);
		SpeechAnswer secondSpeech = saveCompletedSpeechAnswer(question, 2);
		CompletionStage<AnswerSubmissionResult> firstRequest =
			learningAnswerService.submitAsync(
				session.getId(),
				question.getId(),
				firstSpeech.getId()
			).result();

		try {
			assertTrue(assessmentGenerator.awaitAssessmentStarted());
			assertFalse(firstRequest.toCompletableFuture().isDone());

			assertApiException(
				ErrorCode.ANSWER_SUBMISSION_CONFLICT,
				() -> await(learningAnswerService.submitAsync(
					session.getId(),
					question.getId(),
					secondSpeech.getId()
				).result())
			);
			assertTrue(answerSubmissionRepository.findBySpeechAnswer_Id(
				secondSpeech.getId()
			).isEmpty());

			assessmentGenerator.releaseAssessment();
			await(firstRequest);
			assertEquals(1, assessmentGenerator.callCount());
		} finally {
			assessmentGenerator.releaseAssessment();
		}
	}

	@Test
	void 만료된_예약을_재선점하면_이전_토큰은_결과를_저장할_수_없다() {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(question, 1);
		Processing expired = assertInstanceOf(
			Processing.class,
			submissionTransactionService.prepare(
				session.getId(),
				question.getId(),
				speechAnswer.getId()
			)
		);
		int updatedRows = jdbcTemplate.update(
			"update answer_submissions set lease_expires_at = ? where id = ?",
			Timestamp.from(Instant.now().minusSeconds(1)),
			expired.submissionId()
		);

		Processing reclaimed = assertInstanceOf(
			Processing.class,
			submissionTransactionService.prepare(
				session.getId(),
				question.getId(),
				speechAnswer.getId()
			)
		);

		assertEquals(1, updatedRows);
		assertEquals(expired.submissionId(), reclaimed.submissionId());
		assertNotEquals(expired.processingToken(), reclaimed.processingToken());
		assertApiException(
			ErrorCode.ANSWER_SUBMISSION_PROCESSING,
			() -> submissionTransactionService.complete(
				expired.submissionId(),
				expired.processingToken(),
				CORRECT_ASSESSMENT,
				expired.deadline()
			)
		);

		AnswerSubmissionResult completed = submissionTransactionService.complete(
			reclaimed.submissionId(),
			reclaimed.processingToken(),
			CORRECT_ASSESSMENT,
			reclaimed.deadline()
		);
		AnswerSubmission submission = answerSubmissionRepository.findById(
			reclaimed.submissionId()
		).orElseThrow();

		assertEquals(AnswerSubmissionStatus.COMPLETED, submission.getStatus());
		assertEquals(completed.answerId(), submission.getAnswer().getId());
	}

	@Test
	void 동시_한도_초과로_실패한_제출은_자리가_나면_재시도한다()
		throws Exception {
		LearningSession firstSession = saveSession();
		LearningSessionQuestion firstQuestion = firstSession
			.getCurrentQuestion();
		SpeechAnswer firstSpeech = saveCompletedSpeechAnswer(firstQuestion, 1);
		LearningSession secondSession = saveSession();
		LearningSessionQuestion secondQuestion = secondSession
			.getCurrentQuestion();
		SpeechAnswer secondSpeech = saveCompletedSpeechAnswer(secondQuestion, 1);

		CompletionStage<AnswerSubmissionResult> firstRequest =
			learningAnswerService.submitAsync(
				firstSession.getId(),
				firstQuestion.getId(),
				firstSpeech.getId()
			).result();
		assertTrue(assessmentGenerator.awaitAssessmentStarted());
		assertFalse(firstRequest.toCompletableFuture().isDone());

		assertApiException(
			ErrorCode.ANSWER_ASSESSMENT_OVERLOADED,
			() -> await(learningAnswerService.submitAsync(
				secondSession.getId(),
				secondQuestion.getId(),
				secondSpeech.getId()
			).result())
		);
		AnswerSubmission failed = answerSubmissionRepository
			.findBySpeechAnswer_Id(secondSpeech.getId())
			.orElseThrow();
		assertEquals(AnswerSubmissionStatus.FAILED, failed.getStatus());
		assertEquals(
			ErrorCode.ANSWER_ASSESSMENT_OVERLOADED.getMessage(),
			failed.getFailureMessage()
		);
		assertEquals(1, assessmentGenerator.callCount());
		assertEquals(0, answerRepository.count());

		assessmentGenerator.releaseAssessment(0);
		await(firstRequest);
		CompletionStage<AnswerSubmissionResult> retried = learningAnswerService
			.submitAsync(
				secondSession.getId(),
				secondQuestion.getId(),
				secondSpeech.getId()
			)
			.result();
		assertEquals(2, assessmentGenerator.callCount());
		assertFalse(retried.toCompletableFuture().isDone());

		assessmentGenerator.releaseAssessment(1);
		AnswerSubmissionResult recovered = await(retried);
		AnswerSubmission completed = answerSubmissionRepository
			.findById(failed.getId())
			.orElseThrow();

		assertEquals(AnswerSubmissionStatus.COMPLETED, completed.getStatus());
		assertEquals(recovered.answerId(), completed.getAnswer().getId());
		assertEquals(2, answerRepository.count());
		assertEquals(2, answerSubmissionRepository.count());
	}

	private LearningSession saveSession() {
		return LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			"He is ____ing."
		);
	}

	private SpeechAnswer saveCompletedSpeechAnswer(
		LearningSessionQuestion question,
		int recordingNo
	) {
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			question,
			"concurrency-request-" + question.getId() + "-" + recordingNo,
			recordingNo
		);
		speechAnswer.complete("He is running.", 0.94, "TEST_STT");
		return speechAnswerRepository.saveAndFlush(speechAnswer);
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
		AnswerAssessmentConcurrencyLimiter answerAssessmentConcurrencyLimiter() {
			return new AnswerAssessmentConcurrencyLimiter(
				new AnswerAssessmentConcurrencyProperties(1),
				new SimpleMeterRegistry()
			);
		}

		@Bean
		BlockingAnswerAssessmentGenerator answerAssessmentGenerator(
			AnswerAssessmentConcurrencyLimiter concurrencyLimiter
		) {
			return new BlockingAnswerAssessmentGenerator(concurrencyLimiter);
		}
	}

	private static final class BlockingAnswerAssessmentGenerator
		implements AnswerAssessmentGenerator {

		private final AnswerAssessmentConcurrencyLimiter concurrencyLimiter;
		private final List<CompletableFuture<AnswerAssessment>> assessments =
			new CopyOnWriteArrayList<>();
		private final AtomicInteger calls = new AtomicInteger();
		private volatile CountDownLatch assessmentStarted;

		private BlockingAnswerAssessmentGenerator(
			AnswerAssessmentConcurrencyLimiter concurrencyLimiter
		) {
			this.concurrencyLimiter = concurrencyLimiter;
		}

		void reset() {
			calls.set(0);
			assessmentStarted = new CountDownLatch(1);
			assessments.clear();
		}

		boolean awaitAssessmentStarted() throws InterruptedException {
			return assessmentStarted.await(5, SECONDS);
		}

		void releaseAssessment() {
			releaseAssessment(0);
		}

		void releaseAssessment(int index) {
			assessments.get(index).complete(CORRECT_ASSESSMENT);
		}

		void releaseAllAssessments() {
			assessments.forEach(assessment ->
				assessment.complete(CORRECT_ASSESSMENT)
			);
		}

		int callCount() {
			return calls.get();
		}

		@Override
		public AnswerAssessmentTask generateAsync(
			AnswerAssessmentInput input
		) {
			return concurrencyLimiter.execute(() -> {
				calls.incrementAndGet();
				CompletableFuture<AnswerAssessment> assessment =
					new CompletableFuture<>();
				assessments.add(assessment);
				assessmentStarted.countDown();
				return new AnswerAssessmentTask(
					assessment,
					() -> assessment.cancel(true)
				);
			});
		}
	}
}
