package com.malhaebom.malhaebom.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.global.exception.ApiExceptionHandler;
import com.malhaebom.malhaebom.service.LearningHintService;

@ExtendWith(MockitoExtension.class)
class LearningHintControllerTest {

	private static final Long SESSION_ID = 10L;
	private static final Long QUESTION_ID = 20L;
	private static final String HINT_TEXT = "It is a ___.";
	private static final String ENDPOINT =
		"/api/v1/learning-sessions/{sessionId}/questions/{questionId}/hint";

	@Mock
	private LearningHintService learningHintService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(
			new LearningHintController(learningHintService)
		)
			.setControllerAdvice(new ApiExceptionHandler())
			.build();
	}

	@Test
	void 힌트_텍스트와_현재_지원하지_않는_음성_URL을_반환한다()
		throws Exception {
		Question question = createQuestion();
		when(learningHintService.request(SESSION_ID, QUESTION_ID))
			.thenReturn(question);

		mockMvc.perform(post(ENDPOINT, SESSION_ID, QUESTION_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.hintText").value(HINT_TEXT))
			.andExpect(jsonPath("$.data.hintTtsUrl").isEmpty());

		verify(learningHintService).request(SESSION_ID, QUESTION_ID);
	}

	private Question createQuestion() {
		return Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.SHORT_ANSWER,
			"What is this?",
			"이것은 무엇인가요?",
			null,
			"It is a book.",
			Set.of("It is a book."),
			HINT_TEXT,
			null
		);
	}
}
