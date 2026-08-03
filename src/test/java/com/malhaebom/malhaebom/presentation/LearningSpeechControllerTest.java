package com.malhaebom.malhaebom.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.malhaebom.malhaebom.global.exception.ApiExceptionHandler;
import com.malhaebom.malhaebom.global.exception.SpeechProcessingException;
import com.malhaebom.malhaebom.service.SpeechAnswerService;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerResult;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;

@ExtendWith(MockitoExtension.class)
class LearningSpeechControllerTest {

	private static final Long SESSION_ID = 10L;
	private static final Long SESSION_QUESTION_ID = 20L;
	private static final Long SPEECH_ANSWER_ID = 30L;
	private static final String REQUEST_KEY =
		"e23b37e7-d7d4-407e-9f54-dcdaee508799";
	private static final String ENDPOINT =
		"/api/v1/learning-sessions/{sessionId}/questions/"
			+ "{sessionQuestionId}/speech";

	@Mock
	private SpeechAnswerService speechAnswerService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(
			new LearningSpeechController(speechAnswerService)
		)
			.setControllerAdvice(new ApiExceptionHandler())
			.build();
	}

	@Test
	void Multipart_음성_업로드_응답_계약을_반환한다() throws Exception {
		byte[] audioContent = "webm audio".getBytes(StandardCharsets.UTF_8);
		MockMultipartFile audio = audio(
			audioContent,
			"audio/webm;codecs=opus"
		);
		when(
			speechAnswerService.upload(
				any(),
				any(),
				any(),
				any()
			)
		).thenReturn(
			new SpeechAnswerResult(
				SPEECH_ANSWER_ID,
				"He is running.",
				0.94
			)
		);

		mockMvc.perform(
			multipart(ENDPOINT, SESSION_ID, SESSION_QUESTION_ID)
				.file(audio)
				.header("Idempotency-Key", REQUEST_KEY)
		)
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(
				MediaType.APPLICATION_JSON
			))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.speechAnswerId").value(
				SPEECH_ANSWER_ID
			))
			.andExpect(jsonPath("$.data.transcript").value(
				"He is running."
			))
			.andExpect(jsonPath("$.data.confidence").value(0.94))
			.andExpect(jsonPath("$.data.audioUrl").isEmpty())
			.andExpect(jsonPath("$.message").value(
				"음성 변환이 완료되었습니다."
			))
			.andExpect(jsonPath("$.errorCode").doesNotExist());

		ArgumentCaptor<SpeechAudio> audioCaptor =
			ArgumentCaptor.forClass(SpeechAudio.class);
		verify(speechAnswerService).upload(
			eq(SESSION_ID),
			eq(SESSION_QUESTION_ID),
			eq(REQUEST_KEY),
			audioCaptor.capture()
		);
		SpeechAudio captured = audioCaptor.getValue();
		org.assertj.core.api.Assertions.assertThat(captured.content())
			.containsExactly(audioContent);
		org.assertj.core.api.Assertions.assertThat(captured.contentType())
			.isEqualTo("audio/webm;codecs=opus");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"audio/webm",
		"audio/webm;codecs=opus",
		"audio/webm;codecs=vorbis",
		"audio/mp4",
		"audio/mp4;codecs=mp4a.40.2",
		"audio/mp4; codecs=\"mp4a.40.2\"",
		"audio/mpeg"
	})
	void 지원하는_컨테이너_MIME은_코덱_표기와_관계없이_허용한다(
		String contentType
	) throws Exception {
		when(
			speechAnswerService.upload(
				any(),
				any(),
				any(),
				any()
			)
		).thenReturn(
			new SpeechAnswerResult(SPEECH_ANSWER_ID, "Hello.", null)
		);

		mockMvc.perform(
			multipart(ENDPOINT, SESSION_ID, SESSION_QUESTION_ID)
				.file(audio(new byte[] {1}, contentType))
				.header("Idempotency-Key", REQUEST_KEY)
		)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.confidence").isEmpty());
	}

	@Test
	void 빈_파일을_INVALID_AUDIO_FILE로_거부한다() throws Exception {
		mockMvc.perform(
			multipart(ENDPOINT, SESSION_ID, SESSION_QUESTION_ID)
				.file(audio(new byte[0], "audio/webm;codecs=opus"))
				.header("Idempotency-Key", REQUEST_KEY)
		)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.errorCode").value(
				"INVALID_AUDIO_FILE"
			))
			.andExpect(jsonPath("$.message").value(
				"음성 파일은 비어 있을 수 없습니다."
			));

		verifyNoInteractions(speechAnswerService);
	}

	@Test
	void 파일_크기_초과를_INVALID_AUDIO_FILE로_거부한다() throws Exception {
		byte[] oversized = new byte[5 * 1024 * 1024 + 1];

		mockMvc.perform(
			multipart(ENDPOINT, SESSION_ID, SESSION_QUESTION_ID)
				.file(audio(oversized, "audio/webm;codecs=opus"))
				.header("Idempotency-Key", REQUEST_KEY)
		)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value(
				"INVALID_AUDIO_FILE"
			))
			.andExpect(jsonPath("$.message").value(
				"음성 파일은 5MB를 초과할 수 없습니다."
			));

		verifyNoInteractions(speechAnswerService);
	}

	@Test
	void 지원하지_않는_MIME을_INVALID_AUDIO_FILE로_거부한다()
		throws Exception {
		mockMvc.perform(
			multipart(ENDPOINT, SESSION_ID, SESSION_QUESTION_ID)
				.file(audio(new byte[] {1}, "application/octet-stream"))
				.header("Idempotency-Key", REQUEST_KEY)
		)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value(
				"INVALID_AUDIO_FILE"
			))
			.andExpect(jsonPath("$.message").value(
				"지원하지 않는 음성 파일 형식입니다."
			));

		verifyNoInteractions(speechAnswerService);
	}

	@Test
	void Idempotency_Key_누락을_INVALID_REQUEST로_거부한다()
		throws Exception {
		mockMvc.perform(
			multipart(ENDPOINT, SESSION_ID, SESSION_QUESTION_ID)
				.file(audio(new byte[] {1}, "audio/webm;codecs=opus"))
		)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value(
				"Idempotency-Key 헤더는 필수입니다."
			));

		verifyNoInteractions(speechAnswerService);
	}

	@Test
	void 처리_중인_멱등_요청은_409와_SPEECH_PROCESSING을_반환한다()
		throws Exception {
		when(
			speechAnswerService.upload(
				any(),
				any(),
				any(),
				any()
			)
		).thenThrow(new SpeechProcessingException());

		mockMvc.perform(
			multipart(ENDPOINT, SESSION_ID, SESSION_QUESTION_ID)
				.file(audio(new byte[] {1}, "audio/webm;codecs=opus"))
				.header("Idempotency-Key", REQUEST_KEY)
		)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.errorCode").value(
				"SPEECH_PROCESSING"
			));
	}

	private MockMultipartFile audio(byte[] content, String contentType) {
		return new MockMultipartFile(
			"audio",
			"answer.webm",
			contentType,
			content
		);
	}
}
