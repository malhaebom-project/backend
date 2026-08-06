package com.malhaebom.malhaebom.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.malhaebom.malhaebom.domain.User;
import com.malhaebom.malhaebom.global.exception.ApiExceptionHandler;
import com.malhaebom.malhaebom.global.exception.DuplicateEmailException;
import com.malhaebom.malhaebom.presentation.cookie.RefreshCookieProvider;
import com.malhaebom.malhaebom.service.LoginService;
import com.malhaebom.malhaebom.service.UserService;

@ExtendWith(MockitoExtension.class)
class LoginControllerTest {

	private static final String SIGNUP_ENDPOINT = "/api/v1/auth/signup";

	@Mock
	private LoginService loginService;

	@Mock
	private UserService userService;

	@Mock
	private RefreshCookieProvider refreshCookieProvider;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(
			new LoginController(loginService, userService, refreshCookieProvider)
		)
			.setControllerAdvice(new ApiExceptionHandler())
			.build();
	}

	@Test
	void signsUpGuardian() throws Exception {
		User user = User.create("Guardian", "guardian@example.com", "encoded-password");
		when(userService.create("Guardian", "guardian@example.com", "password123"))
			.thenReturn(user);

		mockMvc.perform(post(SIGNUP_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Guardian","email":"guardian@example.com","password":"password123"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.email").value("guardian@example.com"))
			.andExpect(jsonPath("$.data.name").value("Guardian"))
			.andExpect(jsonPath("$.data.role").value("GUARDIAN"));

		verify(userService).create("Guardian", "guardian@example.com", "password123");
	}

	@Test
	void rejectsInvalidSignupRequest() throws Exception {
		mockMvc.perform(post(SIGNUP_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"","email":"invalid","password":"short"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
	}

	@Test
	void rejectsDuplicateEmail() throws Exception {
		when(userService.create("Guardian", "guardian@example.com", "password123"))
			.thenThrow(new DuplicateEmailException());

		mockMvc.perform(post(SIGNUP_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Guardian","email":"guardian@example.com","password":"password123"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_EXISTS"));
	}
}
