package com.malhaebom.malhaebom.integration.learning;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

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
		assertEquals(
			Set.of("He is running.", "He's running."),
			Set.copyOf(transcriber.adaptationPhrases)
		);
		assertFailed(ErrorCode.SPEECH_NOT_RECOGNIZED.getMessage());
	}

	@Test
	void 예상하지_못한_STT_오류는_안전한_실패_정보만_저장한다() {
		RuntimeException providerException = new RuntimeException(
			"secret bucket/key and provider response"
		);
		transcriber.willThrow(providerException);

		assertApiException(
			ErrorCode.STT_PROCESSING_FAILED,
			this::upload
		);

		assertFailed(ErrorCode.STT_PROCESSING_FAILED.getMessage());
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
		private List<String> adaptationPhrases;

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
		public SpeechTranscriptionResult transcribe(
			SpeechAudio audio,
			List<String> adaptationPhrases
		) {
			this.adaptationPhrases = List.copyOf(adaptationPhrases);
			if (exception != null) {
				throw exception;
			}
			return result;
		}
	}
}
