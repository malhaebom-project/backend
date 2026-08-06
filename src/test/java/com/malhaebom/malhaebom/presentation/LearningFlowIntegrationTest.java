package com.malhaebom.malhaebom.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;

@SpringBootTest
@ActiveProfiles({"test", "local-fake-stt"})
@Transactional
class LearningFlowIntegrationTest {

	private static final String TRANSCRIPT = "He is running.";
	private static final String REQUEST_KEY =
		"e23b37e7-d7d4-407e-9f54-dcdaee508799";

	@Autowired
	private WebApplicationContext applicationContext;

	@Autowired
	private SpeechAnswerRepository speechAnswerRepository;

	@Autowired
	private AnswerRepository answerRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders
			.webAppContextSetup(applicationContext)
			.build();
	}

	@Test
	void 문제_제시부터_음성_답변_제출까지_현재_API_흐름이_동작한다()
		throws Exception {
		Long sessionId = createLearningSession();
		Long sessionQuestionId = getNextQuestion(sessionId);
		Long speechAnswerId = uploadSpeech(
			sessionId,
			sessionQuestionId
		);

		submitAnswer(sessionId, sessionQuestionId, speechAnswerId);

		mockMvc.perform(get(
			"/api/v1/learning-sessions/{sessionId}",
			sessionId
		))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.correctCount").value(1));

		assertThat(speechAnswerRepository.findById(speechAnswerId))
			.isPresent()
			.get()
			.extracting(answer -> answer.getTranscript())
			.isEqualTo(TRANSCRIPT);
		assertThat(answerRepository.findAll())
			.singleElement()
			.extracting(answer -> answer.getAnswerText())
			.isEqualTo(TRANSCRIPT);
	}

	private Long createLearningSession() throws Exception {
		MvcResult result = mockMvc.perform(post(
			"/api/v1/learning-sessions"
		)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "childId": 1,
				  "topicId": 3,
				  "difficulty": "EASY",
				  "questionTypes": ["PICTURE_DESCRIPTION"],
				  "questionCount": 1
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
			.andReturn();

		return readLong(result, "$.data.sessionId");
	}

	private Long getNextQuestion(Long sessionId) throws Exception {
		MvcResult result = mockMvc.perform(get(
			"/api/v1/learning-sessions/{sessionId}/questions/next",
			sessionId
		))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.questionText").value(
				"What is the boy doing?"
			))
			.andExpect(jsonPath("$.data.questionIndex").value(1))
			.andReturn();

		return readLong(result, "$.data.sessionQuestionId");
	}

	private Long uploadSpeech(
		Long sessionId,
		Long sessionQuestionId
	) throws Exception {
		MockMultipartFile audio = new MockMultipartFile(
			"audio",
			"answer.webm",
			"audio/webm",
			new byte[] {1, 2, 3}
		);
		MvcResult result = mockMvc.perform(multipart(
			"/api/v1/learning-sessions/{sessionId}/questions/"
				+ "{sessionQuestionId}/speech",
			sessionId,
			sessionQuestionId
		)
			.file(audio)
			.header("Idempotency-Key", REQUEST_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.transcript").value(TRANSCRIPT))
			.andExpect(jsonPath("$.data.confidence").value(0.94))
			.andReturn();

		return readLong(result, "$.data.speechAnswerId");
	}

	private void submitAnswer(
		Long sessionId,
		Long sessionQuestionId,
		Long speechAnswerId
	) throws Exception {
		mockMvc.perform(post(
			"/api/v1/learning-sessions/{sessionId}/questions/"
				+ "{sessionQuestionId}/answers",
			sessionId,
			sessionQuestionId
		)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "speechAnswerId": %d,
				  "answerText": "He is running."
				}
				""".formatted(speechAnswerId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.answerText").value(TRANSCRIPT))
			.andExpect(jsonPath("$.data.result").value("CORRECT"))
			.andExpect(jsonPath("$.data.score").value(100))
			.andExpect(jsonPath("$.data.canRetry").value(false));
	}

	private Long readLong(MvcResult result, String path) throws Exception {
		Number value = JsonPath.read(
			result.getResponse().getContentAsString(),
			path
		);
		return value.longValue();
	}

}
