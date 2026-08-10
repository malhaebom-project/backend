package com.malhaebom.malhaebom.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.AnswerEvaluation;
import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.global.exception.ApiExceptionHandler;
import com.malhaebom.malhaebom.service.LearningAnswerService;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;

@ExtendWith(MockitoExtension.class)
class LearningAnswerControllerTest {

	private static final Long SESSION_ID = 10L;
	private static final Long SESSION_QUESTION_ID = 20L;
	private static final Long SPEECH_ANSWER_ID = 30L;
	private static final String ANSWER_TEXT = "He is running.";
	private static final String ENDPOINT =
		"/api/v1/learning-sessions/{sessionId}/questions/"
			+ "{sessionQuestionId}/answers";

	@Mock
	private LearningAnswerService learningAnswerService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(
			new LearningAnswerController(learningAnswerService)
		)
			.setControllerAdvice(new ApiExceptionHandler())
			.build();
	}

	@Test
	void 답변_제출_요청과_AI_피드백_응답_계약을_반환한다() throws Exception {
		AnswerSubmissionResult submission = answerSubmission();
		when(learningAnswerService.submit(
			SESSION_ID,
			SESSION_QUESTION_ID,
			SPEECH_ANSWER_ID
		)).thenReturn(submission);
		mockMvc.perform(post(ENDPOINT, SESSION_ID, SESSION_QUESTION_ID)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "speechAnswerId": 30
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.attemptNo").value(1))
			.andExpect(jsonPath("$.data.matchedKeywords[0]").value(
				"is running"
			))
			.andExpect(jsonPath("$.data.missingKeywords").isEmpty())
			.andExpect(jsonPath("$.data.feedbackText").value(
				"현재진행형을 정확하게 사용했어요!"
			))
			.andExpect(jsonPath("$.data.feedbackTtsUrl").isEmpty());

		verify(learningAnswerService).submit(
			SESSION_ID,
			SESSION_QUESTION_ID,
			SPEECH_ANSWER_ID
		);
	}

	@Test
	void 클라이언트가_answerText를_보내도_제출_입력으로_사용하지_않는다()
		throws Exception {
		AnswerSubmissionResult submission = answerSubmission();
		when(learningAnswerService.submit(
			SESSION_ID,
			SESSION_QUESTION_ID,
			SPEECH_ANSWER_ID
		)).thenReturn(submission);
		mockMvc.perform(post(ENDPOINT, SESSION_ID, SESSION_QUESTION_ID)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "speechAnswerId": 30,
				  "answerText": "Manipulated client answer."
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.answerText").value(ANSWER_TEXT));

		verify(learningAnswerService).submit(
			SESSION_ID,
			SESSION_QUESTION_ID,
			SPEECH_ANSWER_ID
		);
	}

	@Test
	void speechAnswerId가_없으면_요청을_거부한다() throws Exception {
		mockMvc.perform(post(ENDPOINT, SESSION_ID, SESSION_QUESTION_ID)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

		verifyNoInteractions(learningAnswerService);
	}

	private AnswerSubmissionResult answerSubmission() {
		Question question = Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"소년은 무엇을 하고 있나요?",
			null,
			ANSWER_TEXT,
			Set.of(ANSWER_TEXT),
			null,
			null
		);
		LearningSession session = LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(question)
		);
		LearningSessionQuestion sessionQuestion = session.getCurrentQuestion();
		ReflectionTestUtils.setField(
			sessionQuestion,
			"id",
			SESSION_QUESTION_ID
		);
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			sessionQuestion,
			"request-key",
			1
		);
		speechAnswer.complete(ANSWER_TEXT, 0.94, "TEST_STT");
		Answer answer = Answer.create(
			sessionQuestion,
			speechAnswer,
			1,
			AnswerEvaluation.from(AnswerResult.CORRECT)
		);
		ReflectionTestUtils.setField(answer, "id", 40L);
		AnswerAssessment assessment = new AnswerAssessment(
			true,
			50,
			30,
			20,
			List.of("is running"),
			List.of(),
			"현재진행형을 정확하게 사용했어요!"
		);
		return new AnswerSubmissionResult(answer, assessment, false, 0);
	}
}
