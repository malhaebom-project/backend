package com.malhaebom.malhaebom.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ApiExceptionHandler;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.async.AnswerSubmissionAsyncProperties;
import com.malhaebom.malhaebom.service.LearningAnswerService;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionTask;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;

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
			new LearningAnswerController(
				learningAnswerService,
				new AnswerSubmissionAsyncProperties(Duration.ofSeconds(30))
			)
		)
			.setControllerAdvice(new ApiExceptionHandler())
			.build();
	}

	@Test
	void 음성_답변_ID가_없으면_400으로_거부한다() throws Exception {
		mockMvc.perform(post(
				ENDPOINT,
				SESSION_ID,
				SESSION_QUESTION_ID
			)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"speechAnswerId":null}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
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
		)).thenReturn(submissionTask(submission));

		MvcResult pending = performSubmission()
			.andExpect(request().asyncStarted())
			.andReturn();
		assertEquals(
			Duration.ofSeconds(30).toMillis(),
			pending.getRequest().getAsyncContext().getTimeout()
		);

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
		)).thenReturn(submissionTask(submission));
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

	@Test
	void Servlet_타임아웃이면_제출_작업을_취소하고_504를_반환한다()
		throws Exception {
		CompletableFuture<AnswerSubmissionResult> submission =
			new CompletableFuture<>();
		AtomicBoolean cancelled = new AtomicBoolean();
		when(learningAnswerService.submitAsync(
			SESSION_ID,
			SESSION_QUESTION_ID,
			SPEECH_ANSWER_ID
		)).thenReturn(new AnswerSubmissionTask(
			submission,
			() -> cancelled.compareAndSet(false, true)
		));
		MvcResult pending = performSubmission()
			.andExpect(request().asyncStarted())
			.andReturn();

		MockAsyncContext asyncContext = (MockAsyncContext)pending
			.getRequest()
			.getAsyncContext();
		AsyncEvent timeoutEvent = new AsyncEvent(asyncContext);
		for (AsyncListener listener : asyncContext.getListeners()) {
			listener.onTimeout(timeoutEvent);
		}

		assertTrue(cancelled.get());
		mockMvc.perform(asyncDispatch(pending))
			.andExpect(status().isGatewayTimeout())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.errorCode")
				.value("ANSWER_SUBMISSION_TIMEOUT"));
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

	private AnswerSubmissionTask submissionTask(
		CompletableFuture<AnswerSubmissionResult> result
	) {
		return new AnswerSubmissionTask(result, () -> result.cancel(true));
	}
}
