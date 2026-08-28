package com.malhaebom.malhaebom.integration.learning;

import com.malhaebom.malhaebom.domain.learning.*;
import com.malhaebom.malhaebom.domain.learning.repository.*;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.observability.MicrometerAnswerSubmissionMetricsRecorder;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.service.AnswerSubmissionTransactionService;
import com.malhaebom.malhaebom.service.ChildProfileService;
import com.malhaebom.malhaebom.service.LearningAnswerRetryService;
import com.malhaebom.malhaebom.service.LearningAnswerService;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;
import com.malhaebom.malhaebom.service.exception.AnswerAssessmentOverloadedException;
import com.malhaebom.malhaebom.service.policy.AnswerSubmissionPolicyProperties;
import com.malhaebom.malhaebom.service.port.AnswerAssessmentGenerator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;

@DataJpaTest
@Import(JpaAuditingConfiguration.class)
class LearningAnswerServiceJpaTest {
	private static final String ANSWER_TEXT = "He is running.";
	private static final String PREPARE_METRIC = "malhaebom.answer.submission.prepare";

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
	private EntityManager entityManager;
	@MockitoBean
	private ChildProfileService childProfileService;

	private TestAnswerAssessmentGenerator assessmentGenerator;
	private SimpleMeterRegistry meterRegistry;
	private AnswerSubmissionTransactionService submissionTransactionService;
	private LearningAnswerService learningAnswerService;
	private LearningAnswerRetryService learningAnswerRetryService;

	@BeforeEach
	void setUp() {
		assessmentGenerator = new TestAnswerAssessmentGenerator();
		meterRegistry = new SimpleMeterRegistry();
		Clock clock = Clock.systemUTC();
		submissionTransactionService = new AnswerSubmissionTransactionService(
			learningSessionRepository,
			speechAnswerRepository,
			answerRepository,
			answerSubmissionRepository,
			new AnswerSubmissionPolicyProperties(
				Duration.ofSeconds(25),
				Duration.ofSeconds(60)
			),
			clock,
			childProfileService,
			new MicrometerAnswerSubmissionMetricsRecorder(meterRegistry)
		);
		learningAnswerService = new LearningAnswerService(
			assessmentGenerator,
			submissionTransactionService,
			clock
		);
		learningAnswerRetryService = new LearningAnswerRetryService(
			learningSessionRepository,
			answerRepository,
			answerSubmissionRepository,
			childProfileService
		);
	}

	@Test
	void 저장된_음성_답변의_transcript를_채점하고_결과를_저장한다() {
		LearningSession session = saveSession();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		assessmentGenerator.willReturn(correctAssessment());

		AnswerSubmissionResult result = submit(
			session.getId(),
			session.getCurrentQuestion().getId(),
			speechAnswer.getId()
		);

		assertEquals(1, answerRepository.count());
		assertEquals(ANSWER_TEXT, assessmentGenerator.answerText);
		assertEquals(ANSWER_TEXT, result.answerText());
		assertEquals(1, result.attemptNo());
		assertTrue(result.result().isCorrect());
		assertTrue(session.isCompleted());
		assertFalse(result.canRetry());
		assertEquals(0, result.remainingAttempts());

		answerRepository.flush();
		entityManager.clear();
		Answer savedAnswer = answerRepository.findById(result.answerId())
			.orElseThrow();
		assertEquals(
			"현재진행형을 정확하게 사용했어요!",
			savedAnswer.getFeedbackText()
		);
	}

	@Test
	void 부분_정답의_동적_점수와_재시도_상태를_저장한다() {
		LearningSession session = saveSession();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		assessmentGenerator.willReturn(new AnswerAssessment(
			true,
			40,
			23,
			15,
			"동작 표현은 좋았어요. 주어를 함께 말해 보세요."
		));

		AnswerSubmissionResult result = submit(
			session.getId(),
			session.getCurrentQuestion().getId(),
			speechAnswer.getId()
		);

		assertEquals(AnswerResult.PARTIALLY_CORRECT, result.result());
		assertEquals(78, result.score());
		assertTrue(result.canRetry());
		assertEquals(1, result.remainingAttempts());
		assertEquals(1, session.getCurrentQuestion().getWrongAnswerCount());
	}

	@Test
	void 존재하지_않는_음성_답변은_채점하지_않고_거부한다() {
		LearningSession session = saveSession();

		assertApiException(
			ErrorCode.SPEECH_ANSWER_NOT_FOUND,
			() -> submit(
				session.getId(),
				session.getCurrentQuestion().getId(),
				999L
			)
		);
		assertEquals(0, assessmentGenerator.callCount);
		assertEquals(0, answerRepository.count());
	}

	@Test
	void 다른_문제의_음성_답변은_채점하지_않고_거부한다() {
		LearningSession currentSession = saveSession();
		SpeechAnswer otherSpeechAnswer = saveCompletedSpeechAnswer(
			saveSession()
		);

		assertApiException(
			ErrorCode.CURRENT_QUESTION_MISMATCH,
			() -> submit(
				currentSession.getId(),
				currentSession.getCurrentQuestion().getId(),
				otherSpeechAnswer.getId()
			)
		);
		assertEquals(0, assessmentGenerator.callCount);
		assertEquals(0, answerRepository.count());
	}

	@Test
	void 처리_중인_음성_답변은_채점하지_않고_거부한다() {
		LearningSession session = saveSession();
		SpeechAnswer processing = speechAnswerRepository.saveAndFlush(
			SpeechAnswer.start(
				session.getCurrentQuestion(),
				"processing-request-key",
				1
			)
		);

		assertApiException(
			ErrorCode.INVALID_REQUEST,
			() -> submit(
				session.getId(),
				session.getCurrentQuestion().getId(),
				processing.getId()
			)
		);
		assertEquals(0, assessmentGenerator.callCount);
		assertEquals(0, answerRepository.count());
	}

	@Test
	void 이미_제출에_사용한_음성_답변은_다시_채점하지_않는다() {
		LearningSession session = saveSession();
		LearningSessionQuestion sessionQuestion = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		answerRepository.saveAndFlush(Answer.create(
			sessionQuestion,
			speechAnswer,
			1,
			AnswerEvaluation.from(AnswerResult.INCORRECT),
			"다시 말해 보세요."
		));

		assertApiException(
			ErrorCode.INVALID_REQUEST,
			() -> submit(
				session.getId(),
				sessionQuestion.getId(),
				speechAnswer.getId()
			)
		);
		assertEquals(0, assessmentGenerator.callCount);
		assertEquals(1, answerRepository.count());
	}

	@Test
	void 완료된_동일_제출은_다시_채점하지_않고_기존_결과를_반환한다() {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		assessmentGenerator.willReturn(correctAssessment());

		AnswerSubmissionResult first = submit(
			session.getId(),
			question.getId(),
			speechAnswer.getId()
		);
		AnswerSubmissionResult retried = submit(
			session.getId(),
			question.getId(),
			speechAnswer.getId()
		);

		assertEquals(first.answerId(), retried.answerId());
		assertEquals(1, assessmentGenerator.callCount);
		assertEquals(1, answerRepository.count());
		assertEquals(1, answerSubmissionRepository.count());
		assertEquals(1.0, prepareCount("new"));
		assertEquals(1.0, prepareCount("cached"));
		assertEquals(
			AnswerSubmissionStatus.COMPLETED,
			answerSubmissionRepository.findBySpeechAnswer_Id(
				speechAnswer.getId()
			).orElseThrow().getStatus()
		);
	}

	@Test
	void 채점_실패한_동일_제출은_기존_예약으로_재시도한다() {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		CompletableFuture<AnswerAssessment> assessment =
			new CompletableFuture<>();
		assessmentGenerator.willReturn(assessment);

		CompletionStage<AnswerSubmissionResult> failedSubmission =
			learningAnswerService.submitAsync(LearningJpaTestFixture.USER_ID,
				session.getId(),
				question.getId(),
				speechAnswer.getId()
			).result();
		AnswerSubmission processing = answerSubmissionRepository
			.findBySpeechAnswer_Id(speechAnswer.getId())
			.orElseThrow();
		assertFalse(failedSubmission.toCompletableFuture().isDone());
		assertEquals(
			AnswerSubmissionStatus.PROCESSING,
			processing.getStatus()
		);

		assessment.completeExceptionally(
			new IllegalStateException("OpenAI timeout")
		);

		assertApiException(
			ErrorCode.ANSWER_ASSESSMENT_FAILED,
			() -> await(failedSubmission)
		);
		AnswerSubmission failed = answerSubmissionRepository
			.findBySpeechAnswer_Id(speechAnswer.getId())
			.orElseThrow();
		assertEquals(AnswerSubmissionStatus.FAILED, failed.getStatus());
		assertEquals("OpenAI timeout", failed.getFailureMessage());
		assertEquals(0, answerRepository.count());

		assessmentGenerator.willReturn(correctAssessment());
		AnswerSubmissionResult retried = submit(
			session.getId(),
			question.getId(),
			speechAnswer.getId()
		);

		AnswerSubmission completed = answerSubmissionRepository
			.findBySpeechAnswer_Id(speechAnswer.getId())
			.orElseThrow();
		assertEquals(failed.getId(), completed.getId());
		assertEquals(AnswerSubmissionStatus.COMPLETED, completed.getStatus());
		assertEquals(retried.answerId(), completed.getAnswer().getId());
		assertEquals(2, assessmentGenerator.callCount);
		assertEquals(1, answerRepository.count());
		assertEquals(1, answerSubmissionRepository.count());
		assertEquals(1.0, prepareCount("new"));
		assertEquals(1.0, prepareCount("retry"));
	}

	@Test
	void 채점_동시_요청_한도를_초과하면_예약을_실패시키고_503을_반환한다() {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		assessmentGenerator.willThrow(
			new AnswerAssessmentOverloadedException()
		);

		assertApiException(
			ErrorCode.ANSWER_ASSESSMENT_OVERLOADED,
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
		assertEquals(
			ErrorCode.ANSWER_ASSESSMENT_OVERLOADED.getMessage(),
			failed.getFailureMessage()
		);
		assertEquals(1, assessmentGenerator.callCount);
		assertEquals(0, answerRepository.count());
	}

	@Test
	void 처리_중인_동일_제출은_중복_채점을_거부한다() {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		submissionTransactionService.prepare(LearningJpaTestFixture.USER_ID,
			session.getId(),
			question.getId(),
			speechAnswer.getId()
		);

		assertApiException(
			ErrorCode.ANSWER_SUBMISSION_PROCESSING,
			() -> submit(
				session.getId(),
				question.getId(),
				speechAnswer.getId()
			)
		);
		assertEquals(0, assessmentGenerator.callCount);
		assertEquals(0, answerRepository.count());
		assertEquals(1.0, prepareCount("new"));
		assertEquals(1.0, prepareCount("processing"));
	}

	@Test
	void 처리_임대가_만료된_동일_제출은_새_토큰으로_재시도한다() {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		AnswerSubmission submission = session
			.answerSubmissionTarget(question.getId())
			.reserve(speechAnswer, 1);
		Instant expiredAt = Instant.now().minusSeconds(1);
		submission.claim(
			"215bf1ca-03dc-4a7a-af56-09ad0cc26a24",
			expiredAt.minusSeconds(60),
			expiredAt
		);
		answerSubmissionRepository.saveAndFlush(submission);
		assessmentGenerator.willReturn(correctAssessment());

		AnswerSubmissionResult result = submit(
			session.getId(),
			question.getId(),
			speechAnswer.getId()
		);

		assertTrue(result.result().isCorrect());
		assertEquals(1, assessmentGenerator.callCount);
		assertEquals(
			AnswerSubmissionStatus.COMPLETED,
			answerSubmissionRepository.findById(submission.getId())
				.orElseThrow()
				.getStatus()
		);
		assertEquals(1.0, prepareCount("reclaimed"));
	}

	@Test
	void 처리_중인_문제에_다른_음성_답변을_제출하면_충돌한다() {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer first = saveCompletedSpeechAnswer(session, 1);
		SpeechAnswer second = saveCompletedSpeechAnswer(session, 2);
		submissionTransactionService.prepare(LearningJpaTestFixture.USER_ID,
			session.getId(),
			question.getId(),
			first.getId()
		);

		assertApiException(
			ErrorCode.ANSWER_SUBMISSION_CONFLICT,
			() -> submit(
				session.getId(),
				question.getId(),
				second.getId()
			)
		);
		assertEquals(0, assessmentGenerator.callCount);
		assertEquals(1, answerSubmissionRepository.count());
	}

	@Test
	void 실패한_제출이_있으면_다른_음성_답변으로_시도_번호를_대체할_수_없다() {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer first = saveCompletedSpeechAnswer(session, 1);
		SpeechAnswer second = saveCompletedSpeechAnswer(session, 2);
		assessmentGenerator.willThrow(
			new IllegalStateException("OpenAI timeout")
		);
		assertApiException(
			ErrorCode.ANSWER_ASSESSMENT_FAILED,
			() -> submit(
				session.getId(),
				question.getId(),
				first.getId()
			)
		);

		assertApiException(
			ErrorCode.ANSWER_SUBMISSION_CONFLICT,
			() -> submit(
				session.getId(),
				question.getId(),
				second.getId()
			)
		);
		assertEquals(1, assessmentGenerator.callCount);
		assertEquals(1, answerSubmissionRepository.count());
	}

	@Test
	void 완료된_세션에는_답변을_제출할_수_없다() {
		LearningSession session = saveSession();
		Long sessionQuestionId = session.getCurrentQuestion().getId();
		session.complete();

		assertApiException(
			ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS,
			() -> submit(
				session.getId(),
				sessionQuestionId,
				999L
			)
		);
		assertEquals(0, assessmentGenerator.callCount);
	}

	@Test
	void 다른_사용자는_답변을_제출할_수_없다() {
		LearningSession session = saveSession();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		Long otherUserId = 999L;
		doThrow(new ApiException(ErrorCode.CHILD_ACCESS_DENIED))
			.when(childProfileService).getOwnedActive(otherUserId, session.getChildId());

		assertApiException(ErrorCode.CHILD_ACCESS_DENIED,
			() -> learningAnswerService.submitAsync(
				otherUserId,
				session.getId(),
				session.getCurrentQuestion().getId(),
				speechAnswer.getId()
			));

		assertEquals(0, answerSubmissionRepository.count());
		assertEquals(0, answerRepository.count());
	}

	@Test
	void 첫_오답의_재시도를_건너뛰면_오답_횟수를_유지하고_다음_문제로_이동한다() {
		LearningSession session = saveSessionWithTwoQuestions();
		LearningSessionQuestion skippedQuestion = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		assessmentGenerator.willReturn(incorrectAssessment());
		submit(
			session.getId(),
			skippedQuestion.getId(),
			speechAnswer.getId()
		);

		learningAnswerRetryService.skipRetry(LearningJpaTestFixture.USER_ID,
			session.getId(),
			skippedQuestion.getId()
		);

		assertTrue(skippedQuestion.isCompleted());
		assertFalse(skippedQuestion.isCorrect());
		assertEquals(1, skippedQuestion.getWrongAnswerCount());
		assertEquals(1, session.getCurrentQuestionIndex());
		assertEquals(1, session.getCurrentQuestion().getQuestionIndex());
		assertTrue(session.isInProgress());
	}

	@Test
	void 마지막_문제의_재시도를_건너뛰면_세션이_완료된다() {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		assessmentGenerator.willReturn(incorrectAssessment());
		submit(
			session.getId(),
			question.getId(),
			speechAnswer.getId()
		);

		learningAnswerRetryService.skipRetry(LearningJpaTestFixture.USER_ID, session.getId(), question.getId());

		assertTrue(session.isCompleted());
		assertEquals(1, session.getCurrentQuestionIndex());
	}

	@Test
	void 답변_이력이_없는_문제의_재시도는_건너뛸_수_없다() {
		LearningSession session = saveSession();

		assertApiException(
			ErrorCode.INVALID_REQUEST,
			() -> learningAnswerRetryService.skipRetry(LearningJpaTestFixture.USER_ID,
				session.getId(),
				session.getCurrentQuestion().getId()
			)
		);
	}

	@Test
	void 정답_이력만_있는_문제의_재시도는_건너뛸_수_없다() {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		answerRepository.saveAndFlush(Answer.create(
			question,
			speechAnswer,
			1,
			AnswerEvaluation.from(AnswerResult.CORRECT),
			"정확하게 잘 말했어요!"
		));

		assertApiException(
			ErrorCode.INVALID_REQUEST,
			() -> learningAnswerRetryService.skipRetry(LearningJpaTestFixture.USER_ID,
				session.getId(),
				question.getId()
			)
		);
	}

	@Test
	void 현재_문제가_아닌_문제의_재시도는_건너뛸_수_없다() {
		LearningSession session = saveSession();

		assertApiException(
			ErrorCode.CURRENT_QUESTION_MISMATCH,
			() -> learningAnswerRetryService.skipRetry(LearningJpaTestFixture.USER_ID, session.getId(), 999L)
		);
	}

	@Test
	void 완료된_세션의_재시도는_건너뛸_수_없다() {
		LearningSession session = saveSession();
		session.complete();

		assertApiException(
			ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS,
			() -> learningAnswerRetryService.skipRetry(LearningJpaTestFixture.USER_ID, session.getId(), 999L)
		);
	}

	@Test
	void 존재하지_않는_세션의_재시도는_건너뛸_수_없다() {
		assertApiException(
			ErrorCode.LEARNING_SESSION_NOT_FOUND,
			() -> learningAnswerRetryService.skipRetry(LearningJpaTestFixture.USER_ID, 999L, 999L)
		);
	}

	@Test
	void 다른_사용자는_재시도를_건너뛸_수_없다() {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		Long otherUserId = 999L;
		doThrow(new ApiException(ErrorCode.CHILD_ACCESS_DENIED))
			.when(childProfileService).getOwnedActive(otherUserId, session.getChildId());

		assertApiException(ErrorCode.CHILD_ACCESS_DENIED,
			() -> learningAnswerRetryService.skipRetry(otherUserId, session.getId(), question.getId()));

		assertEquals(0, question.getWrongAnswerCount());
		assertFalse(question.isCompleted());
	}

	private LearningSession saveSession() {
		return LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			"He is ____ing."
		);
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

	private double prepareCount(String result) {
		return meterRegistry.get(PREPARE_METRIC)
			.tag("result", result)
			.counter()
			.count();
	}

	private SpeechAnswer saveCompletedSpeechAnswer(LearningSession session) {
		return saveCompletedSpeechAnswer(session, 1);
	}

	private SpeechAnswer saveCompletedSpeechAnswer(
		LearningSession session,
		int recordingNo
	) {
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			session.getCurrentQuestion(),
			"request-key-" + session.getId() + "-" + recordingNo,
			recordingNo
		);
		speechAnswer.complete(ANSWER_TEXT, 0.94, "TEST_STT");
		return speechAnswerRepository.saveAndFlush(speechAnswer);
	}

	private LearningSession saveSessionWithTwoQuestions() {
		Question first = createQuestion("What is the boy doing?");
		Question second = createQuestion("What is the girl doing?");
		questionRepository.saveAllAndFlush(List.of(first, second));
		return learningSessionRepository.saveAndFlush(
			LearningSession.create(
				1L,
				LearningTopic.DAILY_LIFE,
				Difficulty.EASY,
				List.of(first, second)
			)
		);
	}

	private Question createQuestion(String questionText) {
		return Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			questionText,
			"무엇을 하고 있나요?",
			null,
			"",
			"The child is running.",
			Set.of("The child is running."),
			null,
			null
		);
	}

	private AnswerAssessment correctAssessment() {
		return new AnswerAssessment(
			true,
			50,
			30,
			20,
			"현재진행형을 정확하게 사용했어요!"
		);
	}

	private AnswerAssessment incorrectAssessment() {
		return new AnswerAssessment(
			true,
			10,
			10,
			10,
			"다시 말해 보세요."
		);
	}

	private static final class TestAnswerAssessmentGenerator implements AnswerAssessmentGenerator {

		private CompletionStage<AnswerAssessment> stage;
		private String answerText;
		private int callCount;

		void willReturn(AnswerAssessment assessment) {
			stage = CompletableFuture.completedFuture(assessment);
		}

		void willReturn(CompletionStage<AnswerAssessment> stage) {
			this.stage = stage;
		}

		void willThrow(RuntimeException exception) {
			stage = CompletableFuture.failedFuture(exception);
		}

		@Override
		public AnswerAssessmentTask generateAsync(
			AnswerAssessmentInput input
		) {
			callCount++;
			answerText = input.answerText();
			return new AnswerAssessmentTask(
				stage,
				() -> stage.toCompletableFuture().cancel(true)
			);
		}
	}
}
