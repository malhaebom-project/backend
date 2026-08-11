package com.malhaebom.malhaebom.integration.learning;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.SpeechProcessingStatus;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.global.exception.ApiExceptionHandler;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.presentation.LearningSpeechController;
import com.malhaebom.malhaebom.service.SpeechAnswerService;
import com.malhaebom.malhaebom.service.SpeechAnswerStateService;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

@DataJpaTest
@Import({SpeechAnswerStateService.class, JpaAuditingConfiguration.class})
class LearningSpeechControllerJpaTest {

	private static final String REQUEST_KEY =
		"e23b37e7-d7d4-407e-9f54-dcdaee508799";
	private static final String STT_PROVIDER = "TEST_STT";
	private static final String ENDPOINT =
		"/api/v1/learning-sessions/{sessionId}/questions/"
			+ "{sessionQuestionId}/speech";

	@Autowired
	private SpeechAnswerStateService stateService;
	@Autowired
	private LearningSessionRepository learningSessionRepository;
	@Autowired
	private QuestionRepository questionRepository;
	@Autowired
	private SpeechAnswerRepository speechAnswerRepository;

	private TestSpeechTranscriber transcriber;
	private MockMvc mockMvc;
	private LearningSession session;
	private Long sessionQuestionId;

	@BeforeEach
	void setUp() {
		transcriber = new TestSpeechTranscriber();
		SpeechAnswerService speechAnswerService = new SpeechAnswerService(
			stateService,
			transcriber
		);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new LearningSpeechController(speechAnswerService)
		)
			.setControllerAdvice(new ApiExceptionHandler())
			.build();
		session = saveSession();
		sessionQuestionId = session.getCurrentQuestion().getId();
	}

	@Test
	void Multipart_음성을_변환하고_실제_처리_결과를_저장한다()
		throws Exception {
		byte[] audioContent = "webm audio".getBytes(StandardCharsets.UTF_8);

		mockMvc.perform(
			multipart(ENDPOINT, session.getId(), sessionQuestionId)
				.file(audio(audioContent, "audio/webm;codecs=opus"))
				.header("Idempotency-Key", REQUEST_KEY)
		)
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(
				MediaType.APPLICATION_JSON
			))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.speechAnswerId").isNumber())
			.andExpect(jsonPath("$.data.transcript").value("He is running."))
			.andExpect(jsonPath("$.data.confidence").value(0.94))
			.andExpect(jsonPath("$.data.audioUrl").isEmpty())
			.andExpect(jsonPath("$.message").value(
				"음성 변환이 완료되었습니다."
			));

		SpeechAnswer saved = speechAnswerRepository
			.findByRequestKey(REQUEST_KEY)
			.orElseThrow();
		assertEquals(SpeechProcessingStatus.COMPLETED, saved.getProcessingStatus());
		assertEquals("He is running.", saved.getTranscript());
		assertEquals(STT_PROVIDER, saved.getSttProvider());
		assertEquals(1, transcriber.callCount);
		assertArrayEquals(audioContent, transcriber.audio.content());
		assertEquals(
			"audio/webm;codecs=opus",
			transcriber.audio.contentType()
		);
	}

	@Test
	void 완료된_멱등_요청은_STT와_저장을_반복하지_않는다()
		throws Exception {
		MockMultipartFile audio = audio(new byte[] {1}, "audio/webm");

		performUpload(audio).andExpect(status().isOk());
		performUpload(audio).andExpect(status().isOk());

		assertEquals(1, transcriber.callCount);
		assertEquals(1, speechAnswerRepository.count());
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"audio/webm",
		"audio/mp4",
		"audio/mp4; codecs=\"mp4a.40.2\"",
		"audio/mpeg"
	})
	void 지원하는_컨테이너_MIME은_코덱_표기와_관계없이_허용한다(
		String contentType
	) throws Exception {
		performUpload(audio(new byte[] {1}, contentType))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.confidence").value(0.94));

		assertEquals(1, transcriber.callCount);
		assertEquals(1, speechAnswerRepository.count());
	}

	@Test
	void 빈_파일을_INVALID_AUDIO_FILE로_거부한다() throws Exception {
		performUpload(audio(new byte[0], "audio/webm;codecs=opus"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("INVALID_AUDIO_FILE"))
			.andExpect(jsonPath("$.message").value(
				"음성 파일은 비어 있을 수 없습니다."
			));

		assertNotProcessed();
	}

	@Test
	void 파일_크기_초과를_INVALID_AUDIO_FILE로_거부한다() throws Exception {
		performUpload(audio(
			new byte[5 * 1024 * 1024 + 1],
			"audio/webm;codecs=opus"
		))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("INVALID_AUDIO_FILE"))
			.andExpect(jsonPath("$.message").value(
				"음성 파일은 5MB를 초과할 수 없습니다."
			));

		assertNotProcessed();
	}

	@Test
	void 지원하지_않는_MIME을_INVALID_AUDIO_FILE로_거부한다()
		throws Exception {
		performUpload(audio(new byte[] {1}, "application/octet-stream"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("INVALID_AUDIO_FILE"))
			.andExpect(jsonPath("$.message").value(
				"지원하지 않는 음성 파일 형식입니다."
			));

		assertNotProcessed();
	}

	@Test
	void Idempotency_Key_누락을_INVALID_REQUEST로_거부한다()
		throws Exception {
		mockMvc.perform(
			multipart(ENDPOINT, session.getId(), sessionQuestionId)
				.file(audio(new byte[] {1}, "audio/webm;codecs=opus"))
		)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value(
				"중복 요청 방지를 위한 요청 식별 키가 필요합니다."
			));

		assertNotProcessed();
	}

	private ResultActions performUpload(
		MockMultipartFile audio
	) throws Exception {
		return mockMvc.perform(
			multipart(ENDPOINT, session.getId(), sessionQuestionId)
				.file(audio)
				.header("Idempotency-Key", REQUEST_KEY)
		);
	}

	private void assertNotProcessed() {
		assertEquals(0, transcriber.callCount);
		assertEquals(0, speechAnswerRepository.count());
	}

	private MockMultipartFile audio(byte[] content, String contentType) {
		return new MockMultipartFile(
			"audio",
			"answer.webm",
			contentType,
			content
		);
	}

	private LearningSession saveSession() {
		Question question = questionRepository.saveAndFlush(Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"남자아이는 무엇을 하고 있나요?",
			null,
			"The boy is running.",
			Set.of("He is running.", "He's running."),
			"He is ____ing.",
			null
		));
		return learningSessionRepository.saveAndFlush(LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(question)
		));
	}

	private static final class TestSpeechTranscriber
		implements SpeechTranscriber {

		private int callCount;
		private SpeechAudio audio;

		@Override
		public String provider() {
			return STT_PROVIDER;
		}

		@Override
		public SpeechTranscriptionResult transcribe(SpeechAudio audio) {
			callCount++;
			this.audio = audio;
			return new SpeechTranscriptionResult(
				"He is running.",
				0.94,
				STT_PROVIDER
			);
		}
	}
}
