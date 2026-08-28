package com.malhaebom.malhaebom.integration.user;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.persistence.EntityManager;

import com.malhaebom.malhaebom.domain.AccountRole;
import com.malhaebom.malhaebom.domain.User;
import com.malhaebom.malhaebom.domain.repository.UserRepository;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.auth.PasswordEncoderConfiguration;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.service.UserService;

@DataJpaTest
@Import({
	UserService.class,
	PasswordEncoderConfiguration.class,
	JpaAuditingConfiguration.class
})
class UserServiceJpaTest {
	@Autowired
	private UserService userService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private EntityManager entityManager;

	@Test
	void 보호자를_비밀번호_해시와_함께_저장한다() {
		User created = userService.create(
			"Guardian",
			"guardian@example.com",
			"password123"
		);
		userRepository.flush();
		entityManager.clear();

		User saved = userRepository.findById(created.getId()).orElseThrow();

		assertThat(saved.getName()).isEqualTo("Guardian");
		assertThat(saved.getEmail()).isEqualTo("guardian@example.com");
		assertThat(saved.getRole()).isEqualTo(AccountRole.GUARDIAN);
		assertThat(saved.getPassword()).isNotEqualTo("password123");
		assertThat(passwordEncoder.matches("password123", saved.getPassword()))
			.isTrue();
	}

	@Test
	void 이미_가입한_이메일은_새_사용자를_저장하지_않는다() {
		userService.create(
			"Guardian",
			"guardian@example.com",
			"password123"
		);

		assertApiException(
			ErrorCode.EMAIL_ALREADY_EXISTS,
			() -> userService.create(
				"Other Guardian",
				"guardian@example.com",
				"other-password"
			)
		);
		assertThat(userRepository.count()).isEqualTo(1);
	}
}
