package com.malhaebom.malhaebom.service;

import java.time.Instant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.LoginSession;
import com.malhaebom.malhaebom.domain.User;
import com.malhaebom.malhaebom.domain.repository.LoginSessionRepository;
import com.malhaebom.malhaebom.domain.repository.UserRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.auth.jwt.JwtProperties;
import com.malhaebom.malhaebom.infra.auth.jwt.JwtProvider;
import com.malhaebom.malhaebom.infra.auth.jwt.JwtUserPayload;
import com.malhaebom.malhaebom.service.dto.TokenPair;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class LoginService {

	private final UserRepository userRepository;
	private final LoginSessionRepository loginSessionRepository;
	private final JwtProvider jwtProvider;
	private final JwtProperties jwtProperties;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public TokenPair login(String email, String password) {
		User user = userRepository.findByEmail(email)
			.orElseThrow(this::invalidCredentials);
		if (!passwordEncoder.matches(password, user.getPassword())) {
			throw invalidCredentials();
		}

		TokenPair tokens = createTokens(user.getId());
		loginSessionRepository.deleteByUserId(user.getId());
		loginSessionRepository.save(createSession(user, tokens));
		return tokens;
	}

	@Transactional
	public TokenPair refresh(String refreshToken) {
		LoginSession session = loginSessionRepository
			.findByRefreshToken(refreshToken)
			.orElseThrow(() -> new ApiException(
				ErrorCode.NOT_FOUND,
				"존재하지 않는 로그인 세션입니다."
			));

		JwtUserPayload payload = jwtProvider.parsePayload(
			refreshToken,
			jwtProperties.refresh().signingKey()
		);
		if (!session.getUser().getId().equals(payload.userId())) {
			throw new ApiException(
				ErrorCode.UNAUTHORIZED,
				"리프레시 토큰이 유효하지 않습니다."
			);
		}

		TokenPair tokens = createTokens(payload.userId());
		session.rotate(
			tokens.accessToken(),
			tokens.refreshToken(),
			parseAccessExpiresAt(tokens.accessToken()),
			parseRefreshExpiresAt(tokens.refreshToken())
		);
		return tokens;
	}

	@Transactional
	public void logout(String refreshToken) {
		LoginSession session = loginSessionRepository
			.findByRefreshToken(refreshToken)
			.orElseThrow(() -> new ApiException(
				ErrorCode.NOT_FOUND,
				"존재하지 않는 로그인 세션입니다."
			));
		loginSessionRepository.delete(session);
	}

	private TokenPair createTokens(Long userId) {
		return new TokenPair(
			jwtProvider.createToken(
				userId,
				jwtProperties.access().expiration(),
				jwtProperties.access().signingKey()
			),
			jwtProvider.createToken(
				userId,
				jwtProperties.refresh().expiration(),
				jwtProperties.refresh().signingKey()
			)
		);
	}

	private LoginSession createSession(User user, TokenPair tokens) {
		return LoginSession.create(
			user,
			tokens.accessToken(),
			tokens.refreshToken(),
			parseAccessExpiresAt(tokens.accessToken()),
			parseRefreshExpiresAt(tokens.refreshToken())
		);
	}

	private ApiException invalidCredentials() {
		return new ApiException(
			ErrorCode.UNAUTHORIZED,
			"이메일 또는 비밀번호가 올바르지 않습니다."
		);
	}

	private Instant parseAccessExpiresAt(String token) {
		return jwtProvider.parsePayload(
			token,
			jwtProperties.access().signingKey()
		)
			.expiresAt();
	}

	private Instant parseRefreshExpiresAt(String token) {
		return jwtProvider.parsePayload(
			token,
			jwtProperties.refresh().signingKey()
		)
			.expiresAt();
	}
}
