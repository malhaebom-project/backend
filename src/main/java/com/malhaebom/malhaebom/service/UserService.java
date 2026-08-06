package com.malhaebom.malhaebom.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.User;
import com.malhaebom.malhaebom.domain.repository.UserRepository;
import com.malhaebom.malhaebom.global.exception.DuplicateEmailException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public User create(String name, String email, String password) {
		if (userRepository.existsByEmail(email)) {
			throw new DuplicateEmailException();
		}

		String encodedPassword = passwordEncoder.encode(password);
		return userRepository.save(
			User.create(name, email, encodedPassword)
		);
	}
}
