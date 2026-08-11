package com.malhaebom.malhaebom.integration.learning;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.SpeechProcessingStatus;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.service.SpeechAnswerService;
import com.malhaebom.malhaebom.service.SpeechAnswerStateService;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerResult;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

@DataJpaTest
@Import({SpeechAnswerStateService.class, JpaAuditingConfiguration.class})
class SpeechAnswerServiceJpaTest {

	private static final String REQUEST_KEY =
		"e23b37e7-d7d4-407e-9f54-dcdaee508799";
	private static final String STT_PROVIDER = "TEST_STT";
	private static final SpeechAudio AUDIO = new SpeechAudio(
		new byte[] {1, 2, 3},
		"audio/webm;codecs=opus"
	);

	@Autowired
	private SpeechAnswerStateService stateService;
	@Autowired
	private LearningSessionRepository learningSessionRepository;
	@Autowired
	private QuestionRepository questionRepository;
	@Autowired
	private SpeechAnswerRepository speechAnswerRepository;

	private TestSpeechTranscriber transcriber;
	private SpeechAnswerService speechAnswerService;
	private LearningSession session;
	private Long sessionQuestionId;

	@BeforeEach
	void setUp() {
		transcriber = new TestSpeechTranscriber();
		speechAnswerService = new SpeechAnswerService(
			stateService,
			transcriber
		);
		session = LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			"He is ____ing."
		);
		sessionQuestionId = session.getCurrentQuestion().getId();
	}

	@Test
	void 빈_변환_결과는_실패_상태로_저장한다() {
		transcriber.willReturn(new SpeechTranscriptionResult(
			"  ",
			null,
			STT_PROVIDER
		));

		assertApiException(ErrorCode.SPEECH_NOT_RECOGNIZED, this::upload);
		assertFailed("인식된 발화가 없습니다.");
	}

	@Test
	void STT_타임아웃은_실패_상태를_저장하고_원래_예외를_유지한다() {
		ApiException timeout = new ApiException(
			ErrorCode.STT_PROCESSING_TIMEOUT
		);
		transcriber.willThrow(timeout);

		ApiException thrown = assertThrows(
			ApiException.class,
			this::upload
		);

		assertSame(timeout, thrown);
		assertFailed("STT 처리 시간이 초과되었습니다.");
	}

	@Test
	void STT_요청_제한은_실패_상태를_저장하고_원래_예외를_유지한다() {
		ApiException requestLimit = new ApiException(
			ErrorCode.AI_REQUEST_LIMIT_EXCEEDED
		);
		transcriber.willThrow(requestLimit);

		ApiException thrown = assertThrows(
			ApiException.class,
			this::upload
		);

		assertSame(requestLimit, thrown);
		assertFailed("STT 요청 제한을 초과했습니다.");
	}

	@Test
	void 예상하지_못한_STT_오류는_안전한_실패_정보만_저장한다() {
		RuntimeException providerException = new RuntimeException(
			"secret bucket/key and provider response"
		);
		transcriber.willThrow(providerException);

		ApiException thrown = assertApiException(
			ErrorCode.STT_PROCESSING_FAILED,
			this::upload
		);

		assertSame(providerException, thrown.getCause());
		assertFailed("STT 처리에 실패했습니다.");
	}

	@Test
	void 변환된_STT_실패_예외는_이중_래핑하지_않는다() {
		ApiException processingFailed = new ApiException(
			ErrorCode.STT_PROCESSING_FAILED,
			new RuntimeException("provider response")
		);
		transcriber.willThrow(processingFailed);

		ApiException thrown = assertThrows(
			ApiException.class,
			this::upload
		);

		assertSame(processingFailed, thrown);
		assertFailed("STT 처리에 실패했습니다.");
	}

	private SpeechAnswerResult upload() {
		return speechAnswerService.upload(
			session.getId(),
			sessionQuestionId,
			REQUEST_KEY,
			AUDIO
		);
	}

	private void assertFailed(String expectedMessage) {
		SpeechAnswer saved = speechAnswerRepository
			.findByRequestKey(REQUEST_KEY)
			.orElseThrow();
		assertEquals(SpeechProcessingStatus.FAILED, saved.getProcessingStatus());
		assertEquals(expectedMessage, saved.getFailureMessage());
		assertEquals(STT_PROVIDER, saved.getSttProvider());
	}

	private static final class TestSpeechTranscriber
		implements SpeechTranscriber {

		private SpeechTranscriptionResult result;
		private RuntimeException exception;

		void willReturn(SpeechTranscriptionResult result) {
			this.result = result;
			this.exception = null;
		}

		void willThrow(RuntimeException exception) {
			this.exception = exception;
			this.result = null;
		}

		@Override
		public String provider() {
			return STT_PROVIDER;
		}

		@Override
		public SpeechTranscriptionResult transcribe(SpeechAudio audio) {
			if (exception != null) {
				throw exception;
			}
			return result;
		}
	}
}
