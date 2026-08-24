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
	private Question secondQuestion;
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
			"",
			"The boy is running.",
			Set.of("He is running."),
			null,
			null
		));
		secondQuestion = questionRepository.saveAndFlush(Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.SHORT_ANSWER,
			"What is this?",
			"이것은 무엇인가요?",
			null,
			"",
			"It is a book.",
			Set.of("It is a book."),
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
		secondQuestion.deactivate();
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
				.value("2026-08-18T10:10:00Z"))
			.andExpect(jsonPath("$.data[9].answerId")
				.value(oldestIncluded.getId()));
	}

	@Test
	void 서울_날짜_경계를_UTC_저장값으로_조회한다() throws Exception {
		saveCompletedSession(
			childId,
			LearningTopic.ANIMAL,
			List.of(true),
			LocalDateTime.of(2026, 8, 17, 14, 54, 59),
			LocalDateTime.of(2026, 8, 17, 14, 59, 59)
		);
		LearningSession firstIncluded = saveCompletedSession(
			childId,
			LearningTopic.ANIMAL,
			List.of(true),
			LocalDateTime.of(2026, 8, 17, 14, 55),
			LocalDateTime.of(2026, 8, 17, 15, 0)
		);
		LearningSession lastIncluded = saveCompletedSession(
			childId,
			LearningTopic.FOOD,
			List.of(false),
			LocalDateTime.of(2026, 8, 18, 14, 54, 59),
			LocalDateTime.of(2026, 8, 18, 14, 59, 59)
		);
		saveCompletedSession(
			childId,
			LearningTopic.FOOD,
			List.of(true),
			LocalDateTime.of(2026, 8, 18, 14, 55),
			LocalDateTime.of(2026, 8, 18, 15, 0)
		);

		mockMvc.perform(get(
				"/api/v1/children/{childId}/learning-history",
				childId
			)
				.param("startDate", "2026-08-18")
				.param("endDate", "2026-08-18"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content.length()").value(2))
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.content[0].sessionId")
				.value(lastIncluded.getId()))
			.andExpect(jsonPath("$.data.content[0].completedAt")
				.value("2026-08-18T14:59:59Z"))
			.andExpect(jsonPath("$.data.content[1].sessionId")
				.value(firstIncluded.getId()))
			.andExpect(jsonPath("$.data.content[1].completedAt")
				.value("2026-08-17T15:00:00Z"));
	}

	@Test
	void UTC_완료시각을_서울_날짜로_변환해_연속학습일을_계산한다()
		throws Exception {
		for (int day = 15; day <= 17; day++) {
			LocalDateTime completedAt = LocalDateTime.of(
				2026,
				8,
				day,
				15,
				30
			);
			saveCompletedSession(
				childId,
				LearningTopic.ANIMAL,
				List.of(true),
				completedAt.minusMinutes(5),
				completedAt
			);
		}

		mockMvc.perform(get(
				"/api/v1/children/{childId}/statistics",
				childId
			))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.consecutiveStudyDays").value(3));
	}

	@Test
	void 학습_통계를_실제_저장_결과로_집계한다() throws Exception {
		saveCompletedSession(
			childId,
			LearningTopic.ANIMAL,
			List.of(true, false),
			LocalDateTime.of(2026, 8, 18, 9, 55),
			LocalDateTime.of(2026, 8, 18, 10, 0)
		);
		saveCompletedSession(
			childId,
			LearningTopic.ANIMAL,
			List.of(true),
			LocalDateTime.of(2026, 8, 17, 10, 50),
			LocalDateTime.of(2026, 8, 17, 11, 0)
		);
		saveCompletedSession(
			childId,
			LearningTopic.FOOD,
			List.of(false, true),
			LocalDateTime.of(2026, 8, 16, 8, 53),
			LocalDateTime.of(2026, 8, 16, 9, 0)
		);
		saveCompletedSession(
			childId,
			LearningTopic.DAILY_LIFE,
			List.of(true),
			LocalDateTime.of(2026, 8, 17, 14, 58),
			LocalDateTime.of(2026, 8, 17, 15, 0)
		);
		saveInProgressSession(childId);
		saveCanceledSession(
			childId,
			LocalDateTime.of(2026, 8, 18, 11, 0)
		);
		saveCompletedSession(
			otherChildId,
			LearningTopic.ANIMAL,
			List.of(true),
			LocalDateTime.of(2026, 8, 18, 11, 55),
			LocalDateTime.of(2026, 8, 18, 12, 0)
		);
		question.deactivate();
		secondQuestion.deactivate();
		questionRepository.flush();

		mockMvc.perform(get(
				"/api/v1/children/{childId}/statistics",
				childId
			))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.totalSessionCount").value(4))
			.andExpect(jsonPath("$.data.totalStudySeconds").value(1440))
			.andExpect(jsonPath("$.data.averageCorrectRate").value(66.7))
			.andExpect(jsonPath("$.data.consecutiveStudyDays").value(3))
			.andExpect(jsonPath("$.data.topicStatistics.length()").value(3))
			.andExpect(jsonPath("$.data.topicStatistics[0].topicName")
				.value("동물"))
			.andExpect(jsonPath("$.data.topicStatistics[0].questionCount")
				.value(3))
			.andExpect(jsonPath("$.data.topicStatistics[0].correctRate")
				.value(66.7))
			.andExpect(jsonPath("$.data.topicStatistics[1].topicName")
				.value("음식"))
			.andExpect(jsonPath("$.data.topicStatistics[1].questionCount")
				.value(2))
			.andExpect(jsonPath("$.data.topicStatistics[1].correctRate")
				.value(50.0))
			.andExpect(jsonPath("$.data.topicStatistics[2].topicName")
				.value("일상생활"))
			.andExpect(jsonPath("$.data.topicStatistics[2].questionCount")
				.value(1))
			.andExpect(jsonPath("$.data.topicStatistics[2].correctRate")
				.value(100.0));
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

	private LearningSession saveCompletedSession(
		Long sessionChildId,
		LearningTopic topic,
		List<Boolean> results,
		LocalDateTime startedAt,
		LocalDateTime completedAt
	) {
		List<Question> questions = List.of(question, secondQuestion)
			.subList(0, results.size());
		LearningSession session = LearningSession.create(
			sessionChildId,
			topic,
			Difficulty.EASY,
			questions
		);
		results.forEach(session::completeCurrentQuestion);
		ReflectionTestUtils.setField(session, "startedAt", startedAt);
		ReflectionTestUtils.setField(session, "completedAt", completedAt);
		return learningSessionRepository.saveAndFlush(session);
	}

	private void saveInProgressSession(Long sessionChildId) {
		learningSessionRepository.saveAndFlush(LearningSession.create(
			sessionChildId,
			LearningTopic.ANIMAL,
			Difficulty.EASY,
			List.of(question)
		));
	}

	private void saveCanceledSession(
		Long sessionChildId,
		LocalDateTime completedAt
	) {
		LearningSession session = LearningSession.create(
			sessionChildId,
			LearningTopic.ANIMAL,
			Difficulty.EASY,
			List.of(question)
		);
		session.cancel();
		ReflectionTestUtils.setField(session, "completedAt", completedAt);
		learningSessionRepository.saveAndFlush(session);
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
