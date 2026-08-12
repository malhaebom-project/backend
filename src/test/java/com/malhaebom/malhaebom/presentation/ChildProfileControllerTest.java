package com.malhaebom.malhaebom.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
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
import com.malhaebom.malhaebom.global.exception.ApiExceptionHandler;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.service.ChildProfileService;
import com.malhaebom.malhaebom.service.dto.ChildProfileResult;
import com.malhaebom.malhaebom.service.dto.ChildStatistics;
import com.malhaebom.malhaebom.service.dto.LoginUser;

@ExtendWith(MockitoExtension.class)
class ChildProfileControllerTest {

	private static final Long USER_ID = 1L;
	private static final Long CHILD_ID = 10L;

	@Mock
	private ChildProfileService childProfileService;

	private MockMvc mockMvc;
	private ChildProfile profile;

	@BeforeEach
	void setUp() {
		User user = User.create("Guardian", "guardian@example.com", "encoded-password");
		ReflectionTestUtils.setField(user, "id", USER_ID);
		profile = ChildProfile.create(user, "민수", 10, 3, ChildLevel.BEGINNER);
		ReflectionTestUtils.setField(profile, "id", CHILD_ID);

		mockMvc = MockMvcBuilders.standaloneSetup(
			new ChildProfileController(childProfileService)
		)
			.setCustomArgumentResolvers(loginUserResolver())
			.setControllerAdvice(new ApiExceptionHandler())
			.build();
	}

	@Test
	void 프로필을_생성한다() throws Exception {
		when(childProfileService.create(
			USER_ID,
			"민수",
			10,
			3,
			ChildLevel.BEGINNER
		)).thenReturn(new ChildProfileResult(profile, ChildStatistics.empty()));

		mockMvc.perform(post("/api/v1/children")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"nickname":"민수","age":10,"grade":3,"level":"BEGINNER"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.childId").value(CHILD_ID))
			.andExpect(jsonPath("$.data.nickname").value("민수"))
			.andExpect(jsonPath("$.data.totalStudyCount").value(0))
			.andExpect(jsonPath("$.data.totalCorrectRate").value(0.0))
			.andExpect(jsonPath("$.message").value("어린이 프로필이 생성되었습니다."));
	}

	@Test
	void 잘못된_프로필_생성_요청을_거부한다() throws Exception {
		mockMvc.perform(post("/api/v1/children")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"nickname":"","age":0,"grade":7,"level":null}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
	}

	@Test
	void 프로필을_삭제한다() throws Exception {
		mockMvc.perform(delete("/api/v1/children/{childId}", CHILD_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("어린이 프로필이 삭제되었습니다."));

		verify(childProfileService).delete(USER_ID, CHILD_ID);
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
