package com.malhaebom.malhaebom.service;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.malhaebom.malhaebom.domain.AccountRole;
import com.malhaebom.malhaebom.domain.User;
import com.malhaebom.malhaebom.domain.repository.UserRepository;
import com.malhaebom.malhaebom.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserService userService;

	@Test
	void createsGuardianWithEncodedPassword() {
		String email = "guardian@example.com";
		when(userRepository.existsByEmail(email)).thenReturn(false);
		when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
		when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		User result = userService.create("Guardian", email, "password123");

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(result).isSameAs(captor.getValue());
		assertThat(result.getName()).isEqualTo("Guardian");
		assertThat(result.getEmail()).isEqualTo(email);
		assertThat(result.getPassword()).isEqualTo("encoded-password");
		assertThat(result.getRole()).isEqualTo(AccountRole.GUARDIAN);
	}

	@Test
	void rejectsDuplicateEmail() {
		String email = "guardian@example.com";
		when(userRepository.existsByEmail(email)).thenReturn(true);

		assertApiException(
			ErrorCode.EMAIL_ALREADY_EXISTS,
			() -> userService.create("Guardian", email, "password123")
		);

		verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
		verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
	}
}
