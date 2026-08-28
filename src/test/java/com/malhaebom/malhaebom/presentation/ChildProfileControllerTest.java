package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.global.exception.ApiExceptionHandler;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.service.ChildProfileService;
import com.malhaebom.malhaebom.service.dto.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChildProfileControllerTest {
	private static final Long USER_ID = 1L;

	@Mock
	private ChildProfileService childProfileService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(
			new ChildProfileController(childProfileService)
		)
			.setCustomArgumentResolvers(loginUserResolver())
			.setControllerAdvice(new ApiExceptionHandler())
			.build();
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
