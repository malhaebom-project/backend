package com.malhaebom.malhaebom.integration.learning;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

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

import com.malhaebom.malhaebom.domain.learning.AnswerSubmission;
import com.malhaebom.malhaebom.domain.learning.AnswerSubmissionStatus;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerSubmissionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.infra.time.TimeConfiguration;
import com.malhaebom.malhaebom.service.AnswerAssessmentService;
import com.malhaebom.malhaebom.service.AnswerSubmissionTransactionService;
import com.malhaebom.malhaebom.service.LearningAnswerService;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;
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

	@Test
	void 같은_음성_답변의_동시_제출은_한_요청만_채점한다() throws Exception {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(question, 1);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<AnswerSubmissionResult> firstRequest = null;

		try {
			firstRequest = executor.submit(() -> learningAnswerService.submit(
				session.getId(),
				question.getId(),
				speechAnswer.getId()
			));
			assertTrue(assessmentGenerator.awaitAssessmentStarted());

			assertApiException(
				ErrorCode.ANSWER_SUBMISSION_PROCESSING,
				() -> learningAnswerService.submit(
					session.getId(),
					question.getId(),
					speechAnswer.getId()
				)
			);

			assessmentGenerator.releaseAssessment();
			AnswerSubmissionResult completed = firstRequest.get(10, SECONDS);
			AnswerSubmission submission = answerSubmissionRepository
				.findBySpeechAnswer_Id(speechAnswer.getId())
				.orElseThrow();

			assertEquals(1, assessmentGenerator.callCount());
			assertEquals(AnswerSubmissionStatus.COMPLETED, submission.getStatus());
			assertEquals(completed.answerId(), submission.getAnswer().getId());
		} finally {
			assessmentGenerator.releaseAssessment();
			cancel(firstRequest);
			executor.shutdownNow();
		}
	}

	@Test
	void 같은_문제의_다른_음성_답변도_채점_중에는_제출할_수_없다()
		throws Exception {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer firstSpeech = saveCompletedSpeechAnswer(question, 1);
		SpeechAnswer secondSpeech = saveCompletedSpeechAnswer(question, 2);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<AnswerSubmissionResult> firstRequest = null;

		try {
			firstRequest = executor.submit(() -> learningAnswerService.submit(
				session.getId(),
				question.getId(),
				firstSpeech.getId()
			));
			assertTrue(assessmentGenerator.awaitAssessmentStarted());

			assertApiException(
				ErrorCode.ANSWER_SUBMISSION_CONFLICT,
				() -> learningAnswerService.submit(
					session.getId(),
					question.getId(),
					secondSpeech.getId()
				)
			);
			assertTrue(answerSubmissionRepository.findBySpeechAnswer_Id(
				secondSpeech.getId()
			).isEmpty());

			assessmentGenerator.releaseAssessment();
			firstRequest.get(10, SECONDS);
			assertEquals(1, assessmentGenerator.callCount());
		} finally {
			assessmentGenerator.releaseAssessment();
			cancel(firstRequest);
			executor.shutdownNow();
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

	private void cancel(Future<?> request) {
		if (request != null && !request.isDone()) {
			request.cancel(true);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class AssessmentTestConfiguration {

		@Bean
		BlockingAnswerAssessmentGenerator answerAssessmentGenerator() {
			return new BlockingAnswerAssessmentGenerator();
		}
	}

	private static final class BlockingAnswerAssessmentGenerator
		implements AnswerAssessmentGenerator {

		private final AtomicInteger calls = new AtomicInteger();
		private volatile CountDownLatch assessmentStarted;
		private volatile CountDownLatch assessmentReleased;

		void reset() {
			calls.set(0);
			assessmentStarted = new CountDownLatch(1);
			assessmentReleased = new CountDownLatch(1);
		}

		boolean awaitAssessmentStarted() throws InterruptedException {
			return assessmentStarted.await(5, SECONDS);
		}

		void releaseAssessment() {
			assessmentReleased.countDown();
		}

		int callCount() {
			return calls.get();
		}

		@Override
		public CompletionStage<AnswerAssessment> generateAsync(
			AnswerAssessmentInput input
		) {
			calls.incrementAndGet();
			assessmentStarted.countDown();
			try {
				if (!assessmentReleased.await(10, SECONDS)) {
					return CompletableFuture.failedFuture(
						new IllegalStateException("채점 대기 시간이 초과되었습니다.")
					);
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return CompletableFuture.failedFuture(
					new IllegalStateException("채점 대기가 중단되었습니다.", exception)
				);
			}
			return CompletableFuture.completedFuture(CORRECT_ASSESSMENT);
		}
	}
}
