package com.malhaebom.malhaebom.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.malhaebom.malhaebom.domain.User;
import com.malhaebom.malhaebom.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public User create(String name, String email, String password) {
		String encodedPassword = passwordEncoder.encode(password);
		return userRepository.save(
			User.create(name, email, encodedPassword)
		);
	}
}
