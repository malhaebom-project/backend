package com.malhaebom.malhaebom.integration.learning;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.SpeechProcessingStatus;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.async.SpeechAnswerAsyncProperties;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.service.SpeechAnswerStateService;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerStartResult;

import jakarta.persistence.EntityManager;

@DataJpaTest
@Import({SpeechAnswerStateService.class, JpaAuditingConfiguration.class})
@EnableConfigurationProperties(SpeechAnswerAsyncProperties.class)
class SpeechAnswerStateServiceJpaTest {

	private static final String REQUEST_KEY =
		"e23b37e7-d7d4-407e-9f54-dcdaee508799";
	private static final String STT_PROVIDER = "TEST_STT";

	@Autowired
	private SpeechAnswerStateService stateService;
	@Autowired
	private LearningSessionRepository learningSessionRepository;
	@Autowired
	private QuestionRepository questionRepository;
	@Autowired
	private SpeechAnswerRepository speechAnswerRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private EntityManager entityManager;

	@Test
	void 요청_식별_키가_비어_있으면_요청을_거부한다() {
		assertApiException(
			ErrorCode.INVALID_REQUEST,
			() -> stateService.start(999L, 999L, " ")
		);
	}

	@Test
	void 요청_식별_키가_100자를_초과하면_요청을_거부한다() {
		assertApiException(
			ErrorCode.INVALID_REQUEST,
			() -> stateService.start(999L, 999L, "a".repeat(101))
		);
	}

	@Test
	void 현재_문제의_첫_음성_답변을_저장한다() {
		LearningSession session = saveSession();
		Long sessionQuestionId = currentQuestionId(session);

		SpeechAnswerStartResult startResult = stateService.start(
			session.getId(),
			sessionQuestionId,
			REQUEST_KEY
		);
		SpeechAnswer started = startResult.speechAnswer();

		assertEquals(1, started.getRecordingNo());
		assertEquals(
			List.of("He is running.", "He's running."),
			startResult.adaptationPhrases().stream().sorted().toList()
		);
		assertEquals(
			SpeechProcessingStatus.PROCESSING,
			started.getProcessingStatus()
		);
		assertEquals(1, speechAnswerRepository.count());
	}

	@Test
	void 다시_녹음하면_다음_녹음_순번을_저장한다() {
		LearningSession session = saveSession();
		Long sessionQuestionId = currentQuestionId(session);
		SpeechAnswerStartResult firstStart = stateService.start(
			session.getId(),
			sessionQuestionId,
			"first-request-key"
		);
		SpeechAnswer first = firstStart.speechAnswer();
		stateService.fail(
			first.getId(),
			firstStart.processingToken(),
			"인식 실패",
			STT_PROVIDER
		);

		SpeechAnswer second = stateService.start(
			session.getId(),
			sessionQuestionId,
			"second-request-key"
		).speechAnswer();

		assertEquals(2, second.getRecordingNo());
		assertEquals(2, speechAnswerRepository.count());
	}

	@Test
	void 완료된_멱등_요청은_저장된_결과를_반환한다() {
		LearningSession session = saveSession();
		Long sessionQuestionId = currentQuestionId(session);
		SpeechAnswerStartResult firstStart = stateService.start(
			session.getId(),
			sessionQuestionId,
			REQUEST_KEY
		);
		SpeechAnswer started = firstStart.speechAnswer();
		stateService.complete(
			started.getId(),
			firstStart.processingToken(),
			"He is running.",
			0.94,
			STT_PROVIDER
		);

		SpeechAnswer resolved = stateService.start(
			session.getId(),
			sessionQuestionId,
			REQUEST_KEY
		).speechAnswer();

		assertEquals(started.getId(), resolved.getId());
		assertEquals(SpeechProcessingStatus.COMPLETED, resolved.getProcessingStatus());
		assertEquals(1, speechAnswerRepository.count());
	}

	@Test
	void 처리_중인_멱등_요청은_동일한_처리_토큰으로_반환한다() {
		LearningSession session = saveSession();
		Long sessionQuestionId = currentQuestionId(session);
		SpeechAnswerStartResult first = stateService.start(
			session.getId(),
			sessionQuestionId,
			REQUEST_KEY
		);

		SpeechAnswerStartResult existing = stateService.start(
			session.getId(),
			sessionQuestionId,
			REQUEST_KEY
		);

		assertTrue(first.isClaimed());
		assertTrue(existing.isProcessing());
		assertEquals(first.processingToken(), existing.processingToken());
		assertEquals(
			first.speechAnswer().getId(),
			existing.speechAnswer().getId()
		);
		assertEquals(1, speechAnswerRepository.count());
	}

	@Test
	void 만료된_PROCESSING은_새_토큰으로_회수하고_이전_완료를_차단한다() {
		LearningSession session = saveSession();
		Long sessionQuestionId = currentQuestionId(session);
		SpeechAnswerStartResult first = stateService.start(
			session.getId(),
			sessionQuestionId,
			REQUEST_KEY
		);
		Long speechAnswerId = first.speechAnswer().getId();
		jdbcTemplate.update(
			"update speech_answers set lease_expires_at = ? where id = ?",
			Timestamp.from(Instant.now().minusSeconds(1)),
			speechAnswerId
		);
		entityManager.clear();

		SpeechAnswerStartResult reclaimed = stateService.start(
			session.getId(),
			sessionQuestionId,
			REQUEST_KEY
		);

		assertTrue(reclaimed.isClaimed());
		assertNotEquals(first.processingToken(), reclaimed.processingToken());
		assertTrue(stateService.complete(
			speechAnswerId,
			first.processingToken(),
			"old result",
			0.1,
			STT_PROVIDER
		).isEmpty());
		entityManager.clear();
		SpeechAnswer processing = speechAnswerRepository.findById(speechAnswerId)
			.orElseThrow();
		assertEquals(
			SpeechProcessingStatus.PROCESSING,
			processing.getProcessingStatus()
		);
		assertEquals(
			reclaimed.processingToken(),
			processing.getProcessingToken()
		);

		assertTrue(stateService.complete(
			speechAnswerId,
			reclaimed.processingToken(),
			"new result",
			0.9,
			STT_PROVIDER
		).isPresent());
		speechAnswerRepository.flush();
		entityManager.clear();
		SpeechAnswer completed = speechAnswerRepository.findById(speechAnswerId)
			.orElseThrow();
		assertEquals(
			SpeechProcessingStatus.COMPLETED,
			completed.getProcessingStatus()
		);
		assertEquals("new result", completed.getTranscript());
	}

	@Test
	void 실패한_멱등_요청은_새_행을_만들지_않는다() {
		LearningSession session = saveSession();
		Long sessionQuestionId = currentQuestionId(session);
		SpeechAnswerStartResult firstStart = stateService.start(
			session.getId(),
			sessionQuestionId,
			REQUEST_KEY
		);
		SpeechAnswer started = firstStart.speechAnswer();
		stateService.fail(
			started.getId(),
			firstStart.processingToken(),
			"STT 처리 실패",
			STT_PROVIDER
		);

		assertApiException(
			ErrorCode.STT_PROCESSING_FAILED,
			() -> stateService.start(
				session.getId(),
				sessionQuestionId,
				REQUEST_KEY
			)
		);
		assertEquals(1, speechAnswerRepository.count());
	}

	@Test
	void 현재_문제가_아니면_음성_답변을_저장하지_않는다() {
		LearningSession session = saveSession();

		assertApiException(
			ErrorCode.CURRENT_QUESTION_MISMATCH,
			() -> stateService.start(
				session.getId(),
				999L,
				REQUEST_KEY
			)
		);
		assertEquals(0, speechAnswerRepository.count());
	}

	@Test
	void 다른_문제의_멱등키는_재사용할_수_없다() {
		LearningSession firstSession = saveSession();
		LearningSession secondSession = saveSession();
		stateService.start(
			firstSession.getId(),
			currentQuestionId(firstSession),
			REQUEST_KEY
		);

		assertApiException(
			ErrorCode.CURRENT_QUESTION_MISMATCH,
			() -> stateService.start(
				secondSession.getId(),
				currentQuestionId(secondSession),
				REQUEST_KEY
			)
		);
		assertEquals(1, speechAnswerRepository.count());
	}

	@Test
	void 존재하지_않는_음성_답변은_전용_예외로_거부한다() {
		assertApiException(
			ErrorCode.SPEECH_ANSWER_NOT_FOUND,
			() -> stateService.complete(
				999L,
				"missing-processing-token",
				"He is running.",
				0.94,
				STT_PROVIDER
			)
		);
	}

	@Test
	void 완료된_세션에서는_음성_답변을_시작할_수_없다() {
		LearningSession session = saveSession();
		Long sessionQuestionId = currentQuestionId(session);
		session.complete();

		assertApiException(
			ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS,
			() -> stateService.start(
				session.getId(),
				sessionQuestionId,
				REQUEST_KEY
			)
		);
		assertEquals(0, speechAnswerRepository.count());
	}

	private LearningSession saveSession() {
		return LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			"He is ____ing."
		);
	}

	private Long currentQuestionId(LearningSession session) {
		LearningSessionQuestion currentQuestion = session.getCurrentQuestion();
		return currentQuestion.getId();
	}
}
