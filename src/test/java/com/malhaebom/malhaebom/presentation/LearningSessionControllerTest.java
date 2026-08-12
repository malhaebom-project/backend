package com.malhaebom.malhaebom.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.global.exception.ApiExceptionHandler;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.infra.storage.image.QuestionImageProperties;
import com.malhaebom.malhaebom.infra.storage.image.QuestionImageUrlResolver;
import com.malhaebom.malhaebom.service.LearningSessionService;
import com.malhaebom.malhaebom.service.dto.LoginUser;

@ExtendWith(MockitoExtension.class)
class LearningSessionControllerTest {

	private static final Long USER_ID = 1L;
	private static final Long SESSION_ID = 10L;
	private static final String ENDPOINT =
		"/api/v1/learning-sessions/{sessionId}/complete";

	@Mock
	private LearningSessionService learningSessionService;
	private QuestionImageUrlResolver questionImageUrlResolver;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		questionImageUrlResolver = new QuestionImageUrlResolver(
			new QuestionImageProperties("https://assets.example.com")
		);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new LearningSessionController(
				learningSessionService,
				questionImageUrlResolver
			)
		)
			.setCustomArgumentResolvers(loginUserResolver())
			.setControllerAdvice(new ApiExceptionHandler())
			.build();
	}

	@Test
	void 로그인한_보호자의_프로필로_학습_세션을_생성한다() throws Exception {
		LearningSession session = createSession();
		ReflectionTestUtils.setField(session, "id", SESSION_ID);
		when(learningSessionService.create(
			USER_ID,
			1L,
			1L,
			Difficulty.EASY,
			List.of(QuestionType.SHORT_ANSWER),
			1
		)).thenReturn(session);

		mockMvc.perform(post("/api/v1/learning-sessions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "childId":1,
					  "topicId":1,
					  "difficulty":"EASY",
					  "questionTypes":["SHORT_ANSWER"],
					  "questionCount":1
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.childId").value(1))
			.andExpect(jsonPath("$.data.sessionId").value(SESSION_ID));

		verify(learningSessionService).create(
			USER_ID,
			1L,
			1L,
			Difficulty.EASY,
			List.of(QuestionType.SHORT_ANSWER),
			1
		);
	}

	@Test
	void 다음_문제의_이미지_URL을_완성해서_반환한다() throws Exception {
		Question question = Question.create(
			LearningTopic.ANIMAL,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			"How does the animal feel?",
			"동물의 기분이 어떤가요?",
			"/question-images/easy/animal/animal-feelings.webp",
			"It is happy.",
			Set.of("It is happy."),
			null,
			null
		);
		ReflectionTestUtils.setField(question, "id", 20L);
		LearningSession session = LearningSession.create(
			1L,
			LearningTopic.ANIMAL,
			Difficulty.EASY,
			List.of(question)
		);
		ReflectionTestUtils.setField(session, "id", SESSION_ID);
		LearningSessionQuestion sessionQuestion = session.getCurrentQuestion();
		ReflectionTestUtils.setField(sessionQuestion, "id", 30L);
		String resolvedImageUrl =
			"https://assets.example.com/question-images/easy/animal/"
				+ "animal-feelings.webp";

		when(learningSessionService.getNextQuestion(SESSION_ID))
			.thenReturn(sessionQuestion);
		mockMvc.perform(get(
			"/api/v1/learning-sessions/{sessionId}/questions/next",
			SESSION_ID
		))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.imageUrl").value(resolvedImageUrl));
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

	private HandlerMethodArgumentResolver loginUserResolver() {
		return new HandlerMethodArgumentResolver() {
			@Override
			public boolean supportsParameter(MethodParameter parameter) {
				return parameter.hasParameterAnnotation(Auth.class)
					&& parameter.getParameterType().equals(LoginUser.class);
			}

			@Override
			public Object resolveArgument(
				MethodParameter parameter,
				ModelAndViewContainer mavContainer,
				NativeWebRequest webRequest,
				WebDataBinderFactory binderFactory
			) {
				return new LoginUser(USER_ID);
			}
		};
	}
}
