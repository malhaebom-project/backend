package com.malhaebom.malhaebom.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ApiExceptionHandler;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.LearningAnswerService;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;

@ExtendWith(MockitoExtension.class)
class LearningAnswerControllerTest {

	private static final Long SESSION_ID = 1L;
	private static final Long SESSION_QUESTION_ID = 2L;
	private static final Long SPEECH_ANSWER_ID = 3L;
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
	void 답변_제출은_Servlet_비동기로_처리하고_완료_후_응답한다()
		throws Exception {
		CompletableFuture<AnswerSubmissionResult> submission =
			new CompletableFuture<>();
		when(learningAnswerService.submitAsync(
			SESSION_ID,
			SESSION_QUESTION_ID,
			SPEECH_ANSWER_ID
		)).thenReturn(submission);

		MvcResult pending = performSubmission()
			.andExpect(request().asyncStarted())
			.andReturn();

		submission.complete(submissionResult());

		mockMvc.perform(asyncDispatch(pending))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.answerId").value(10L))
			.andExpect(jsonPath("$.data.sessionQuestionId")
				.value(SESSION_QUESTION_ID))
			.andExpect(jsonPath("$.data.result").value("CORRECT"))
			.andExpect(jsonPath("$.data.score").value(100));

		verify(learningAnswerService).submitAsync(
			SESSION_ID,
			SESSION_QUESTION_ID,
			SPEECH_ANSWER_ID
		);
	}

	@Test
	void 비동기_제출의_API_예외를_기존_오류_응답으로_반환한다()
		throws Exception {
		CompletableFuture<AnswerSubmissionResult> submission =
			new CompletableFuture<>();
		when(learningAnswerService.submitAsync(
			SESSION_ID,
			SESSION_QUESTION_ID,
			SPEECH_ANSWER_ID
		)).thenReturn(submission);
		MvcResult pending = performSubmission()
			.andExpect(request().asyncStarted())
			.andReturn();

		submission.completeExceptionally(
			new ApiException(ErrorCode.ANSWER_ASSESSMENT_OVERLOADED)
		);

		mockMvc.perform(asyncDispatch(pending))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.errorCode")
				.value("ANSWER_ASSESSMENT_OVERLOADED"));
	}

	private ResultActions performSubmission() throws Exception {
		return mockMvc.perform(post(
				ENDPOINT,
				SESSION_ID,
				SESSION_QUESTION_ID
			)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"speechAnswerId":3}
				"""));
	}

	private AnswerSubmissionResult submissionResult() {
		return new AnswerSubmissionResult(
			10L,
			SESSION_QUESTION_ID,
			1,
			"It is a book.",
			AnswerResult.CORRECT,
			100,
			"It is a book.",
			"정확하게 잘 말했어요!",
			false,
			2
		);
	}
}
