package com.malhaebom.malhaebom.integration.learning;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.malhaebom.malhaebom.domain.User;
import com.malhaebom.malhaebom.domain.child.ChildLevel;
import com.malhaebom.malhaebom.domain.child.ChildProfile;
import com.malhaebom.malhaebom.domain.child.repository.ChildProfileRepository;
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
import com.malhaebom.malhaebom.domain.learning.repository.AnswerRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.domain.repository.UserRepository;
import com.malhaebom.malhaebom.global.exception.ApiExceptionHandler;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.infra.storage.image.QuestionImageProperties;
import com.malhaebom.malhaebom.infra.storage.image.QuestionImageUrlResolver;
import com.malhaebom.malhaebom.presentation.LearningRecordController;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.service.ChildProfileService;
import com.malhaebom.malhaebom.service.LearningRecordQueryService;
import com.malhaebom.malhaebom.service.dto.LoginUser;

@DataJpaTest
@Import({
	ChildProfileService.class,
	LearningRecordQueryService.class,
	JpaAuditingConfiguration.class,
	LearningRecordControllerJpaTest.RecordTestConfiguration.class
})
class LearningRecordControllerJpaTest {

	private static final String IMAGE_BASE_URL = "https://cdn.test";
	private static final String IMAGE_PATH = "questions/boy-running.png";

	@Autowired
	private LearningRecordQueryService learningRecordQueryService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ChildProfileRepository childProfileRepository;
	@Autowired
	private QuestionRepository questionRepository;
	@Autowired
	private LearningSessionRepository learningSessionRepository;
	@Autowired
	private SpeechAnswerRepository speechAnswerRepository;
	@Autowired
	private AnswerRepository answerRepository;

	private MockMvc mockMvc;
	private Long userId;
	private Long childId;
	private Long otherChildId;
	private Question question;
	private int requestSequence;

	@BeforeEach
	void setUp() {
		User user = userRepository.saveAndFlush(User.create(
			"Guardian",
			"guardian@example.com",
			"encoded-password"
		));
		ChildProfile child = childProfileRepository.saveAndFlush(
			ChildProfile.create(
				user,
				"민수",
				10,
				3,
				ChildLevel.BEGINNER
			)
		);
		ChildProfile otherChild = childProfileRepository.saveAndFlush(
			ChildProfile.create(
				user,
				"영희",
				9,
				2,
				ChildLevel.BEGINNER
			)
		);
		userId = user.getId();
		childId = child.getId();
		otherChildId = otherChild.getId();
		question = questionRepository.saveAndFlush(Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"남자아이는 무엇을 하고 있나요?",
			IMAGE_PATH,
			"The boy is running.",
			Set.of("He is running."),
			null,
			null
		));
		mockMvc = MockMvcBuilders.standaloneSetup(
			new LearningRecordController(learningRecordQueryService)
		)
			.setCustomArgumentResolvers(loginUserResolver())
			.setControllerAdvice(new ApiExceptionHandler())
			.build();
	}

	@Test
	void 최근_오답_10건을_실제_저장_결과로_조회한다() throws Exception {
		LocalDateTime baseTime = LocalDateTime.of(2026, 8, 18, 10, 0);
		List<Answer> wrongAnswers = new ArrayList<>();
		AnswerResult[] wrongResults = {
			AnswerResult.INCORRECT,
			AnswerResult.PARTIALLY_CORRECT,
			AnswerResult.UNRECOGNIZED
		};
		for (int index = 0; index < 11; index++) {
			wrongAnswers.add(saveAnswer(
				childId,
				wrongResults[index % wrongResults.length],
				"wrong answer " + index,
				"피드백 " + index,
				baseTime.plusMinutes(index)
			));
		}
		saveAnswer(
			childId,
			AnswerResult.CORRECT,
			"The boy is running.",
			"정답이에요.",
			baseTime.plusHours(1)
		);
		saveAnswer(
			otherChildId,
			AnswerResult.INCORRECT,
			"He is walking.",
			"다른 어린이의 피드백",
			baseTime.plusHours(2)
		);
		question.deactivate();
		questionRepository.flush();

		Answer newest = wrongAnswers.get(10);
		Answer oldestIncluded = wrongAnswers.get(1);
		mockMvc.perform(get(
				"/api/v1/children/{childId}/wrong-answers",
				childId
			))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.length()").value(10))
			.andExpect(jsonPath("$.data[0].answerId").value(newest.getId()))
			.andExpect(jsonPath("$.data[0].questionId").value(question.getId()))
			.andExpect(jsonPath("$.data[0].questionText")
				.value("What is the boy doing?"))
			.andExpect(jsonPath("$.data[0].imageUrl")
				.value(IMAGE_BASE_URL + "/" + IMAGE_PATH))
			.andExpect(jsonPath("$.data[0].answerText")
				.value("wrong answer 10"))
			.andExpect(jsonPath("$.data[0].modelAnswer")
				.value("The boy is running."))
			.andExpect(jsonPath("$.data[0].feedbackText")
				.value("피드백 10"))
			.andExpect(jsonPath("$.data[0].answeredAt")
				.value("2026-08-18T10:10:00"))
			.andExpect(jsonPath("$.data[9].answerId")
				.value(oldestIncluded.getId()));
	}

	private Answer saveAnswer(
		Long answerChildId,
		AnswerResult result,
		String answerText,
		String feedbackText,
		LocalDateTime submittedAt
	) {
		LearningSession session = learningSessionRepository.saveAndFlush(
			LearningSession.create(
				answerChildId,
				LearningTopic.DAILY_LIFE,
				Difficulty.EASY,
				List.of(question)
			)
		);
		LearningSessionQuestion sessionQuestion = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			sessionQuestion,
			"wrong-answer-request-" + requestSequence++,
			1
		);
		speechAnswer.complete(answerText, 0.9, "TEST_STT");
		speechAnswerRepository.saveAndFlush(speechAnswer);

		Answer answer = Answer.create(
			sessionQuestion,
			speechAnswer,
			1,
			AnswerEvaluation.from(result),
			feedbackText
		);
		ReflectionTestUtils.setField(answer, "submittedAt", submittedAt);
		return answerRepository.saveAndFlush(answer);
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
				return new LoginUser(userId);
			}
		};
	}

	@TestConfiguration
	static class RecordTestConfiguration {

		@Bean
		Clock clock() {
			return Clock.fixed(
				Instant.parse("2026-08-18T03:00:00Z"),
				ZoneOffset.UTC
			);
		}

		@Bean
		QuestionImageUrlResolver questionImageUrlResolver() {
			return new QuestionImageUrlResolver(
				new QuestionImageProperties(IMAGE_BASE_URL)
			);
		}
	}
}
