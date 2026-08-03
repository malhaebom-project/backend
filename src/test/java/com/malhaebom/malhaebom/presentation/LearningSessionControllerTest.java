package com.malhaebom.malhaebom.presentation;

import static org.mockito.Mockito.verify;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.global.exception.ApiExceptionHandler;
import com.malhaebom.malhaebom.service.LearningSessionService;

@ExtendWith(MockitoExtension.class)
class LearningSessionControllerTest {

	private static final Long SESSION_ID = 10L;
	private static final String ENDPOINT =
		"/api/v1/learning-sessions/{sessionId}/complete";

	@Mock
	private LearningSessionService learningSessionService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(
			new LearningSessionController(learningSessionService)
		)
			.setControllerAdvice(new ApiExceptionHandler())
			.build();
	}

	@Test
	void 완료된_세션의_현재_완료_결과를_반환한다() throws Exception {
		LearningSession session = createSession();
		ReflectionTestUtils.setField(session, "id", SESSION_ID);
		session.completeCurrentQuestion(true);
		when(learningSessionService.complete(SESSION_ID)).thenReturn(session);

		mockMvc.perform(post(ENDPOINT, SESSION_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.sessionId").value(SESSION_ID))
			.andExpect(jsonPath("$.data.status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.currentQuestionIndex").value(1))
			.andExpect(jsonPath("$.data.questionCount").value(1))
			.andExpect(jsonPath("$.data.correctCount").value(1))
			.andExpect(jsonPath("$.data.correctRate").value(100))
			.andExpect(jsonPath("$.data.studySeconds").isNumber())
			.andExpect(jsonPath("$.data.completedAt").isNotEmpty())
			.andExpect(jsonPath("$.message").value("학습을 완료했습니다."));

		verify(learningSessionService).complete(SESSION_ID);
	}

	@Test
	void 모든_문제를_완료하지_않은_세션은_완료를_거부한다()
		throws Exception {
		when(learningSessionService.complete(SESSION_ID)).thenThrow(
			new IllegalStateException(
				"모든 문제를 완료한 학습 세션이 아닙니다."
			)
		);

		mockMvc.perform(post(ENDPOINT, SESSION_ID))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value(
				"모든 문제를 완료한 학습 세션이 아닙니다."
			));
	}

	private LearningSession createSession() {
		Question question = Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.SHORT_ANSWER,
			"What is this?",
			"이것은 무엇인가요?",
			null,
			"It is a book.",
			Set.of("It is a book."),
			null,
			null
		);
		return LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(question)
		);
	}
}
