package com.malhaebom.malhaebom.integration.learning;

import com.malhaebom.malhaebom.domain.learning.*;
import com.malhaebom.malhaebom.domain.learning.repository.*;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.ai.*;
import com.malhaebom.malhaebom.infra.observability.AnswerAssessmentMetricsRecorder;
import com.malhaebom.malhaebom.infra.observability.MicrometerAnswerAssessmentMetricsRecorder;
import com.malhaebom.malhaebom.infra.observability.MicrometerAnswerSubmissionMetricsRecorder;
import com.malhaebom.malhaebom.infra.observability.ProviderRateLimitMetricsRecorder;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.service.AnswerSubmissionTransactionService;
import com.malhaebom.malhaebom.service.ChildProfileService;
import com.malhaebom.malhaebom.service.LearningAnswerService;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionPreparation.Processing;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;
import com.malhaebom.malhaebom.service.port.AnswerAssessmentGenerator;
import io.github.bucket4j.TimeMeter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.*;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
	"malhaebom.answer-submission.processing-timeout=25s",
	"malhaebom.answer-submission.processing-lease=60s"
})
@Import({
	JpaAuditingConfiguration.class,
	LearningAnswerService.class,
	MicrometerAnswerAssessmentMetricsRecorder.class,
	MicrometerAnswerSubmissionMetricsRecorder.class,
	AnswerSubmissionTransactionService.class,
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
	private ControllableQueueTimeoutScheduler queueTimeoutScheduler;
	@Autowired
	private TestClock clock;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@MockitoBean
	private ChildProfileService childProfileService;

	@BeforeEach
	void setUp() {
		assessmentGenerator.reset();
		queueTimeoutScheduler.reset();
		clock.reset();
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
			learningAnswerService.submitAsync(LearningJpaTestFixture.USER_ID,
				session.getId(),
				question.getId(),
				speechAnswer.getId()
			).result();

		try {
			assertTrue(assessmentGenerator.awaitAssessmentStarted());
			assertFalse(firstRequest.toCompletableFuture().isDone());

			assertApiException(
				ErrorCode.ANSWER_SUBMISSION_PROCESSING,
				() -> await(learningAnswerService.submitAsync(LearningJpaTestFixture.USER_ID,
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
			learningAnswerService.submitAsync(LearningJpaTestFixture.USER_ID,
				session.getId(),
				question.getId(),
				firstSpeech.getId()
			).result();

		try {
			assertTrue(assessmentGenerator.awaitAssessmentStarted());
			assertFalse(firstRequest.toCompletableFuture().isDone());

			assertApiException(
				ErrorCode.ANSWER_SUBMISSION_CONFLICT,
				() -> await(learningAnswerService.submitAsync(LearningJpaTestFixture.USER_ID,
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
			submissionTransactionService.prepare(LearningJpaTestFixture.USER_ID,
				session.getId(),
				question.getId(),
				speechAnswer.getId()
			)
		);
		int updatedRows = jdbcTemplate.update(
			"update answer_submissions set lease_expires_at = ? where id = ?",
			Timestamp.from(clock.instant().minusSeconds(1)),
			expired.submissionId()
		);

		Processing reclaimed = assertInstanceOf(
			Processing.class,
			submissionTransactionService.prepare(LearningJpaTestFixture.USER_ID,
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
	void queue에_여유가_있으면_제출은_pending으로_기다린_뒤_FIFO로_완료된다()
		throws Exception {
		LearningSession firstSession = saveSession();
		LearningSessionQuestion firstQuestion = firstSession
			.getCurrentQuestion();
		SpeechAnswer firstSpeech = saveCompletedSpeechAnswer(firstQuestion, 1);
		LearningSession secondSession = saveSession();
		LearningSessionQuestion secondQuestion = secondSession
			.getCurrentQuestion();
		SpeechAnswer secondSpeech = saveCompletedSpeechAnswer(secondQuestion, 1);
		long answersBefore = answerRepository.count();
		long submissionsBefore = answerSubmissionRepository.count();

		CompletionStage<AnswerSubmissionResult> firstRequest =
			learningAnswerService.submitAsync(LearningJpaTestFixture.USER_ID,
				firstSession.getId(),
				firstQuestion.getId(),
				firstSpeech.getId()
			).result();
		assertTrue(assessmentGenerator.awaitAssessmentStarted());
		assertFalse(firstRequest.toCompletableFuture().isDone());

		CompletionStage<AnswerSubmissionResult> secondRequest =
			learningAnswerService.submitAsync(LearningJpaTestFixture.USER_ID,
				secondSession.getId(),
				secondQuestion.getId(),
				secondSpeech.getId()
			).result();
		AnswerSubmission queued = answerSubmissionRepository
			.findBySpeechAnswer_Id(secondSpeech.getId())
			.orElseThrow();

		assertFalse(secondRequest.toCompletableFuture().isDone());
		assertEquals(AnswerSubmissionStatus.PROCESSING, queued.getStatus());
		assertEquals(1, assessmentGenerator.callCount());
		assertEquals(2, queueTimeoutScheduler.scheduledCount());
		assertEquals(answersBefore, answerRepository.count());

		assessmentGenerator.releaseAssessment(0);
		await(firstRequest);
		assertEquals(2, assessmentGenerator.callCount());
		assertFalse(secondRequest.toCompletableFuture().isDone());
		assertEquals(0, queueTimeoutScheduler.scheduledCount());

		assessmentGenerator.releaseAssessment(1);
		AnswerSubmissionResult secondResult = await(secondRequest);
		AnswerSubmission completed = answerSubmissionRepository.findById(
			queued.getId()
		).orElseThrow();

		assertEquals(AnswerSubmissionStatus.COMPLETED, completed.getStatus());
		assertEquals(secondResult.answerId(), completed.getAnswer().getId());
		assertEquals(answersBefore + 2, answerRepository.count());
		assertEquals(
			submissionsBefore + 2,
			answerSubmissionRepository.count()
		);
	}

	@Test
	void rate_queue가_차면_그_다음_제출을_실패시키고_같은_예약으로_재시도한다()
		throws Exception {
		SubmissionFixture first = submissionFixture(1);
		SubmissionFixture second = submissionFixture(1);
		SubmissionFixture third = submissionFixture(1);
		long answersBefore = answerRepository.count();
		long submissionsBefore = answerSubmissionRepository.count();
		CompletionStage<AnswerSubmissionResult> firstRequest = submitAsync(first);
		assertTrue(assessmentGenerator.awaitAssessmentStarted());
		CompletionStage<AnswerSubmissionResult> secondRequest = submitAsync(second);

		assertApiException(
			ErrorCode.ANSWER_ASSESSMENT_OVERLOADED,
			() -> await(submitAsync(third))
		);
		AnswerSubmission failed = answerSubmissionRepository
			.findBySpeechAnswer_Id(third.speechAnswer().getId())
			.orElseThrow();
		assertEquals(AnswerSubmissionStatus.FAILED, failed.getStatus());
		assertEquals(
			ErrorCode.ANSWER_ASSESSMENT_OVERLOADED.getMessage(),
			failed.getFailureMessage()
		);
		assertEquals(1, assessmentGenerator.callCount());
		assertEquals(2, queueTimeoutScheduler.scheduledCount());
		assertEquals(answersBefore, answerRepository.count());

		assessmentGenerator.releaseAssessment(0);
		await(firstRequest);
		assertEquals(2, assessmentGenerator.callCount());
		CompletionStage<AnswerSubmissionResult> retried = submitAsync(third);
		AnswerSubmission processingRetry = answerSubmissionRepository.findById(
			failed.getId()
		).orElseThrow();
		assertEquals(
			AnswerSubmissionStatus.PROCESSING,
			processingRetry.getStatus()
		);
		assertFalse(retried.toCompletableFuture().isDone());

		assessmentGenerator.releaseAssessment(1);
		await(secondRequest);
		assertEquals(3, assessmentGenerator.callCount());
		assessmentGenerator.releaseAssessment(2);
		AnswerSubmissionResult recovered = await(retried);
		AnswerSubmission completed = answerSubmissionRepository.findById(
			failed.getId()
		).orElseThrow();

		assertEquals(AnswerSubmissionStatus.COMPLETED, completed.getStatus());
		assertEquals(recovered.answerId(), completed.getAnswer().getId());
		assertEquals(answersBefore + 3, answerRepository.count());
		assertEquals(
			submissionsBefore + 3,
			answerSubmissionRepository.count()
		);
	}

	@Test
	void queue_wait_timeout은_503으로_예약을_실패시키고_provider를_호출하지_않는다()
		throws Exception {
		SubmissionFixture first = submissionFixture(1);
		SubmissionFixture queued = submissionFixture(1);
		long answersBefore = answerRepository.count();
		CompletionStage<AnswerSubmissionResult> firstRequest = submitAsync(first);
		assertTrue(assessmentGenerator.awaitAssessmentStarted());
		CompletionStage<AnswerSubmissionResult> queuedRequest = submitAsync(queued);

		queueTimeoutScheduler.expireNext();

		assertApiException(
			ErrorCode.ANSWER_ASSESSMENT_OVERLOADED,
			() -> await(queuedRequest)
		);
		AnswerSubmission failed = answerSubmissionRepository
			.findBySpeechAnswer_Id(queued.speechAnswer().getId())
			.orElseThrow();
		assertEquals(AnswerSubmissionStatus.FAILED, failed.getStatus());
		assertEquals(
			ErrorCode.ANSWER_ASSESSMENT_OVERLOADED.getMessage(),
			failed.getFailureMessage()
		);
		assertEquals(1, assessmentGenerator.callCount());
		assertEquals(0, queueTimeoutScheduler.scheduledCount());

		assessmentGenerator.releaseAssessment(0);
		await(firstRequest);
		CompletionStage<AnswerSubmissionResult> retried = submitAsync(queued);
		assertEquals(2, assessmentGenerator.callCount());
		assessmentGenerator.releaseAssessment(1);
		AnswerSubmissionResult recovered = await(retried);
		AnswerSubmission completed = answerSubmissionRepository.findById(
			failed.getId()
		).orElseThrow();

		assertEquals(AnswerSubmissionStatus.COMPLETED, completed.getStatus());
		assertEquals(recovered.answerId(), completed.getAnswer().getId());
		assertEquals(answersBefore + 2, answerRepository.count());
	}

	@Test
	void queued_제출을_취소하면_provider_호출_없이_제거하고_504로_실패시킨다()
		throws Exception {
		SubmissionFixture first = submissionFixture(1);
		SubmissionFixture queued = submissionFixture(1);
		CompletionStage<AnswerSubmissionResult> firstRequest = submitAsync(first);
		assertTrue(assessmentGenerator.awaitAssessmentStarted());
		var queuedTask = learningAnswerService.submitAsync(LearningJpaTestFixture.USER_ID,
			queued.session().getId(),
			queued.question().getId(),
			queued.speechAnswer().getId()
		);

		assertTrue(queuedTask.cancel());
		assertApiException(
			ErrorCode.ANSWER_SUBMISSION_TIMEOUT,
			() -> await(queuedTask.result())
		);
		AnswerSubmission failed = answerSubmissionRepository
			.findBySpeechAnswer_Id(queued.speechAnswer().getId())
			.orElseThrow();
		assertEquals(AnswerSubmissionStatus.FAILED, failed.getStatus());
		assertEquals(
			ErrorCode.ANSWER_SUBMISSION_TIMEOUT.getMessage(),
			failed.getFailureMessage()
		);
		assertEquals(1, assessmentGenerator.callCount());
		assertEquals(0, queueTimeoutScheduler.scheduledCount());

		assessmentGenerator.releaseAssessment(0);
		await(firstRequest);
		assertEquals(1, assessmentGenerator.callCount());
	}

	@Test
	void 호출자_취소는_실패_기록의_row_lock보다_queue_용량을_먼저_반환한다()
		throws Exception {
		SubmissionFixture first = submissionFixture(1);
		SubmissionFixture cancelled = submissionFixture(1);
		SubmissionFixture replacement = submissionFixture(1);
		CompletionStage<AnswerSubmissionResult> firstRequest = submitAsync(first);
		assertTrue(assessmentGenerator.awaitAssessmentStarted());
		var cancelledTask = learningAnswerService.submitAsync(LearningJpaTestFixture.USER_ID,
			cancelled.session().getId(),
			cancelled.question().getId(),
			cancelled.speechAnswer().getId()
		);
		Long cancelledSubmissionId = answerSubmissionRepository
			.findBySpeechAnswer_Id(cancelled.speechAnswer().getId())
			.orElseThrow()
			.getId();
		CountDownLatch rowLocked = new CountDownLatch(1);
		CountDownLatch releaseRowLock = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<?> lock = executor.submit(() -> {
				TransactionTemplate transaction = new TransactionTemplate(
					transactionManager
				);
				transaction.executeWithoutResult(status -> {
					jdbcTemplate.queryForObject(
						"select id from answer_submissions "
							+ "where id = ? for update",
						Long.class,
						cancelledSubmissionId
					);
					rowLocked.countDown();
					await(releaseRowLock);
				});
			});
			assertTrue(rowLocked.await(5, SECONDS));
			Future<Boolean> cancellation = executor.submit(cancelledTask::cancel);

			assertTrue(queueTimeoutScheduler.awaitCancellation());
			var replacementTask = learningAnswerService.submitAsync(LearningJpaTestFixture.USER_ID,
				replacement.session().getId(),
				replacement.question().getId(),
				replacement.speechAnswer().getId()
			);
			assertFalse(replacementTask.result().toCompletableFuture().isDone());

			releaseRowLock.countDown();
			lock.get(5, SECONDS);
			assertTrue(cancellation.get(5, SECONDS));
			assertApiException(
				ErrorCode.ANSWER_SUBMISSION_TIMEOUT,
				() -> await(cancelledTask.result())
			);

			assessmentGenerator.releaseAssessment(0);
			await(firstRequest);
			assertEquals(2, assessmentGenerator.callCount());
			assessmentGenerator.releaseAssessment(1);
			await(replacementTask.result());
		} finally {
			releaseRowLock.countDown();
			assessmentGenerator.releaseAllAssessments();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(5, SECONDS));
		}
	}

	@Test
	void queue에서_사용한_시간은_기존_25초_deadline에서_차감된다()
		throws Exception {
		SubmissionFixture first = submissionFixture(1);
		SubmissionFixture queued = submissionFixture(1);
		long answersBefore = answerRepository.count();
		CompletionStage<AnswerSubmissionResult> firstRequest = submitAsync(first);
		assertTrue(assessmentGenerator.awaitAssessmentStarted());
		CompletionStage<AnswerSubmissionResult> queuedRequest = submitAsync(queued);

		clock.advance(Duration.ofSeconds(10));
		assessmentGenerator.releaseAssessment(0);
		await(firstRequest);
		assertEquals(2, assessmentGenerator.callCount());

		clock.advance(Duration.ofSeconds(15));
		assessmentGenerator.releaseAssessment(1);

		assertApiException(
			ErrorCode.ANSWER_SUBMISSION_TIMEOUT,
			() -> await(queuedRequest)
		);
		AnswerSubmission failed = answerSubmissionRepository
			.findBySpeechAnswer_Id(queued.speechAnswer().getId())
			.orElseThrow();
		assertEquals(AnswerSubmissionStatus.FAILED, failed.getStatus());
		assertEquals(
			ErrorCode.ANSWER_SUBMISSION_TIMEOUT.getMessage(),
			failed.getFailureMessage()
		);
		assertEquals(answersBefore + 1, answerRepository.count());
	}

	private SubmissionFixture submissionFixture(int recordingNo) {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(
			question,
			recordingNo
		);
		return new SubmissionFixture(session, question, speechAnswer);
	}

	private CompletionStage<AnswerSubmissionResult> submitAsync(
		SubmissionFixture fixture
	) {
		return learningAnswerService.submitAsync(LearningJpaTestFixture.USER_ID,
			fixture.session().getId(),
			fixture.question().getId(),
			fixture.speechAnswer().getId()
		).result();
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
		AnswerAssessmentRateLimitQueue answerAssessmentRateLimitQueue(
			ControllableQueueTimeoutScheduler timeoutScheduler,
			AnswerAssessmentMetricsRecorder metrics,
			OpenAiAnswerAssessmentRateLimiter rateLimiter
		) {
			return new AnswerAssessmentRateLimitQueue(
				new AnswerAssessmentQueueProperties(
					1,
					Duration.ofSeconds(10)
				),
				metrics,
				rateLimiter,
				timeoutScheduler,
				System::nanoTime
			);
		}

		@Bean
		ManualRateTimeMeter rateTimeMeter() {
			return new ManualRateTimeMeter();
		}

		@Bean
		OpenAiAnswerAssessmentRateLimiter rateLimiter(
			ManualRateTimeMeter timeMeter
		) {
			return new OpenAiAnswerAssessmentRateLimiter(
				new OpenAiAnswerAssessmentRateLimitProperties(1, 3_000, 3_000),
				ProviderRateLimitMetricsRecorder.NOOP,
				timeMeter
			);
		}

		@Bean
		SimpleMeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

		@Bean
		ControllableQueueTimeoutScheduler queueTimeoutScheduler() {
			return new ControllableQueueTimeoutScheduler();
		}

		@Bean
		TestClock clock() {
			return new TestClock();
		}

		@Bean
		BlockingAnswerAssessmentGenerator answerAssessmentGenerator(
			AnswerAssessmentRateLimitQueue rateLimitQueue,
			ControllableQueueTimeoutScheduler timeoutScheduler,
			ManualRateTimeMeter timeMeter
		) {
			return new BlockingAnswerAssessmentGenerator(
				rateLimitQueue,
				timeoutScheduler,
				timeMeter
			);
		}
	}

	private void await(CountDownLatch latch) {
		try {
			assertTrue(latch.await(5, SECONDS));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}

	private record SubmissionFixture(
		LearningSession session,
		LearningSessionQuestion question,
		SpeechAnswer speechAnswer
	) {
	}

	private static final class ControllableQueueTimeoutScheduler
		implements AnswerAssessmentQueueTimeoutScheduler {

		private final Queue<ScheduledTimeout> timeouts = new ArrayDeque<>();
		private CountDownLatch cancellationObserved = new CountDownLatch(1);

		@Override
		public synchronized TimeoutHandle schedule(
			Runnable task,
			Duration delay
		) {
			ScheduledTimeout timeout = new ScheduledTimeout(task);
			timeouts.add(timeout);
			return () -> cancel(timeout);
		}

		void expireNext() {
			ScheduledTimeout timeout;
			synchronized (this) {
				timeout = timeouts.poll();
			}
			if (timeout != null && !timeout.cancelled) {
				timeout.task.run();
			}
		}

		void runRateRetry() {
			ScheduledTimeout retry;
			synchronized (this) {
				retry = timeouts.stream().reduce((first, second) -> second)
					.orElse(null);
				if (retry != null) {
					timeouts.remove(retry);
				}
			}
			if (retry != null && !retry.cancelled) {
				retry.task.run();
			}
		}

		synchronized int scheduledCount() {
			return timeouts.size();
		}

		synchronized void reset() {
			timeouts.clear();
			cancellationObserved = new CountDownLatch(1);
		}

		boolean awaitCancellation() throws InterruptedException {
			return cancellationObserved.await(5, SECONDS);
		}

		private synchronized void cancel(ScheduledTimeout timeout) {
			timeout.cancelled = true;
			timeouts.remove(timeout);
			cancellationObserved.countDown();
		}

		private static final class ScheduledTimeout {

			private final Runnable task;
			private volatile boolean cancelled;

			private ScheduledTimeout(Runnable task) {
				this.task = task;
			}
		}
	}

	private static final class ManualRateTimeMeter implements TimeMeter {
		private final AtomicLong nanos = new AtomicLong();

		@Override
		public long currentTimeNanos() {
			return nanos.get();
		}

		@Override
		public boolean isWallClockBased() {
			return false;
		}

		void refill() {
			nanos.addAndGet(Duration.ofMinutes(1).toNanos());
		}
	}

	private static final class TestClock extends Clock {
		private static final Instant INITIAL_INSTANT = Instant.parse(
			"2026-08-23T00:00:00Z"
		);

		private Instant current = INITIAL_INSTANT;

		synchronized void reset() {
			current = INITIAL_INSTANT;
		}

		synchronized void advance(Duration duration) {
			current = current.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			if (ZoneOffset.UTC.equals(zone)) {
				return this;
			}
			return Clock.fixed(instant(), zone);
		}

		@Override
		public synchronized Instant instant() {
			return current;
		}
	}

	private static final class BlockingAnswerAssessmentGenerator
		implements AnswerAssessmentGenerator {

		private final AnswerAssessmentRateLimitQueue rateLimitQueue;
		private final ControllableQueueTimeoutScheduler timeoutScheduler;
		private final ManualRateTimeMeter timeMeter;
		private final List<CompletableFuture<AnswerAssessment>> assessments =
			new CopyOnWriteArrayList<>();
		private final AtomicInteger calls = new AtomicInteger();
		private volatile CountDownLatch assessmentStarted;

		private BlockingAnswerAssessmentGenerator(
			AnswerAssessmentRateLimitQueue rateLimitQueue,
			ControllableQueueTimeoutScheduler timeoutScheduler,
			ManualRateTimeMeter timeMeter
		) {
			this.rateLimitQueue = rateLimitQueue;
			this.timeoutScheduler = timeoutScheduler;
			this.timeMeter = timeMeter;
		}

		void reset() {
			timeMeter.refill();
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
			timeMeter.refill();
			timeoutScheduler.runRateRetry();
		}

		void releaseAllAssessments() {
			for (int index = 0; index < assessments.size(); index++) {
				releaseAssessment(index);
			}
		}

		int callCount() {
			return calls.get();
		}

		@Override
		public AnswerAssessmentTask generateAsync(
			AnswerAssessmentInput input
		) {
			return rateLimitQueue.execute(() -> {
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
