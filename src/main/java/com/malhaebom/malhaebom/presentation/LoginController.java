package com.malhaebom.malhaebom.presentation;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malhaebom.malhaebom.presentation.cookie.RefreshCookieProvider;
import com.malhaebom.malhaebom.presentation.dto.AccessTokenResponse;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.LoginRequest;
import com.malhaebom.malhaebom.service.LoginService;
import com.malhaebom.malhaebom.service.dto.TokenPair;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LoginController {

	private final LoginService loginService;
	private final RefreshCookieProvider refreshCookieProvider;

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<AccessTokenResponse>> login(
		@Valid @RequestBody LoginRequest request
	) {
		TokenPair tokens = loginService.login(
			request.email(),
			request.password()
		);
		return tokenResponse(tokens, "로그인되었습니다.");
	}

	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<AccessTokenResponse>> refresh(
		@CookieValue(RefreshCookieProvider.REFRESH_TOKEN_KEY) String refreshToken
	) {
		TokenPair tokens = loginService.refresh(refreshToken);
		return tokenResponse(tokens, "토큰이 재발급되었습니다.");
	}

	@DeleteMapping("/logout")
	public ResponseEntity<Void> logout(
		@CookieValue(RefreshCookieProvider.REFRESH_TOKEN_KEY) String refreshToken
	) {
		loginService.logout(refreshToken);
		ResponseCookie cookie = refreshCookieProvider.expire();
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, cookie.toString())
			.build();
	}

	private ResponseEntity<ApiResponse<AccessTokenResponse>> tokenResponse(
		TokenPair tokens,
		String message
	) {
		ResponseCookie cookie = refreshCookieProvider.create(tokens.refreshToken());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookie.toString())
			.body(ApiResponse.success(
				new AccessTokenResponse(tokens.accessToken()),
				message
			));
	}
}
