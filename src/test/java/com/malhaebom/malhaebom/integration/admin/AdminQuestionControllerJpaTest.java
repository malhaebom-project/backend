package com.malhaebom.malhaebom.integration.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.malhaebom.malhaebom.domain.User;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.domain.repository.UserRepository;
import com.malhaebom.malhaebom.global.exception.ApiExceptionHandler;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.infra.storage.image.QuestionImageProperties;
import com.malhaebom.malhaebom.infra.storage.image.QuestionImageUrlResolver;
import com.malhaebom.malhaebom.presentation.AdminQuestionController;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.service.AdminQuestionService;
import com.malhaebom.malhaebom.service.dto.LoginUser;

@DataJpaTest
@Import({
	AdminQuestionService.class,
	JpaAuditingConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminQuestionControllerJpaTest {

	@Autowired
	private AdminQuestionService adminQuestionService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private QuestionRepository questionRepository;

	private MockMvc mockMvc;
	private Long adminId;
	private Question question;

	@BeforeEach
	void setUp() {
		questionRepository.deleteAll();
		userRepository.deleteAll();

		User admin = userRepository.saveAndFlush(User.createAdmin(
			"Admin",
			"admin@example.com",
			"encoded-password"
		));
		adminId = admin.getId();
		question = saveQuestion(
			"What is the boy doing?",
			Set.of("He is running.", "The boy is running.")
		);

		QuestionImageUrlResolver imageUrlResolver =
			new QuestionImageUrlResolver(
				new QuestionImageProperties("https://cdn.test")
			);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new AdminQuestionController(
				adminQuestionService,
				imageUrlResolver
			)
		)
			.setCustomArgumentResolvers(loginUserResolver())
			.setControllerAdvice(new ApiExceptionHandler())
			.build();
	}

	@Test
	void 관리자_문제_단건_조회는_OSIV_없이_허용_답안을_반환한다()
		throws Exception {
		mockMvc.perform(get(
				"/api/v1/admin/questions/{questionId}",
				question.getId()
			))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.questionId").value(question.getId()))
			.andExpect(jsonPath("$.data.gradingContext")
				.value("소년이 달리고 있다."))
			.andExpect(jsonPath("$.data.acceptedAnswers", containsInAnyOrder(
				"He is running.",
				"The boy is running."
			)));
	}

	@Test
	void 관리자_문제_목록_조회는_OSIV_없이_허용_답안을_반환한다()
		throws Exception {
		Question newestQuestion = saveQuestion(
			"What is this?",
			Set.of("It is a book.", "This is a book.")
		);

		mockMvc.perform(get("/api/v1/admin/questions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].questionId")
				.value(newestQuestion.getId()))
			.andExpect(jsonPath(
				"$.data[0].acceptedAnswers",
				containsInAnyOrder(
					"It is a book.",
					"This is a book."
				)
			));
	}

	private Question saveQuestion(
		String questionText,
		Set<String> acceptedAnswers
	) {
		return questionRepository.saveAndFlush(Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			questionText,
			"한글 문제",
			null,
			"소년이 달리고 있다.",
			acceptedAnswers.iterator().next(),
			acceptedAnswers,
			null,
			null
		));
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
				return new LoginUser(adminId);
			}
		};
	}
}
