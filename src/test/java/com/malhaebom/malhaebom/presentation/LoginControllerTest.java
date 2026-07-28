package com.malhaebom.malhaebom.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.User;
import com.malhaebom.malhaebom.domain.repository.LoginSessionRepository;
import com.malhaebom.malhaebom.domain.repository.UserRepository;
import com.malhaebom.malhaebom.presentation.cookie.RefreshCookieProvider;
import com.malhaebom.malhaebom.service.UserService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private LoginSessionRepository loginSessionRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void 이메일과_비밀번호로_로그인한다() throws Exception {
		String rawPassword = "password123!";
		userService.create("사용자", "user@example.com", rawPassword);

		User user = userRepository.findByEmail("user@example.com").orElseThrow();
		assertThat(user.getPassword()).isNotEqualTo(rawPassword);
		assertThat(passwordEncoder.matches(rawPassword, user.getPassword()))
			.isTrue();

		mockMvc.perform(
				post("/api/v1/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{
						  "email": "user@example.com",
						  "password": "password123!"
						}
						""")
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.message").value("로그인되었습니다."))
			.andExpect(header().string(
				HttpHeaders.SET_COOKIE,
				containsString(
					RefreshCookieProvider.REFRESH_TOKEN_KEY + "="
				)
			));

		assertThat(loginSessionRepository.findAll()).hasSize(1);
	}

	@Test
	void 비밀번호가_다르면_로그인할_수_없다() throws Exception {
		userService.create(
			"사용자",
			"user@example.com",
			"password123!"
		);

		mockMvc.perform(
				post("/api/v1/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{
						  "email": "user@example.com",
						  "password": "wrong-password"
						}
						""")
			)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.message").value(
				"이메일 또는 비밀번호가 올바르지 않습니다."
			));

		assertThat(loginSessionRepository.findAll()).isEmpty();
	}
}
