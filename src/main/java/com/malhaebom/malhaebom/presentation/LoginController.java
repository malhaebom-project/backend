package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.infra.openapi.ValidationErrorResponses;
import com.malhaebom.malhaebom.presentation.cookie.RefreshCookieProvider;
import com.malhaebom.malhaebom.presentation.dto.*;
import com.malhaebom.malhaebom.service.LoginService;
import com.malhaebom.malhaebom.service.UserService;
import com.malhaebom.malhaebom.service.dto.TokenPair;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "회원가입, 로그인과 JWT 토큰을 관리하는 API")
public class LoginController {
	private final LoginService loginService;
	private final UserService userService;
	private final RefreshCookieProvider refreshCookieProvider;

	@PostMapping("/signup")
	@Operation(summary = "회원가입")
	@ValidationErrorResponses
	public ResponseEntity<ApiResponse<UserResponse>> signup(@Valid @RequestBody SignupRequest request) {
		UserResponse user = UserResponse.from(userService.create(
			request.name(),
			request.email(),
			request.password()
		));
		return ResponseEntity.status(201)
			.body(ApiResponse.success(user, "회원가입되었습니다."));
	}

	@PostMapping("/login")
	@Operation(summary = "로그인")
	@ValidationErrorResponses
	public ResponseEntity<ApiResponse<AccessTokenResponse>> login(@Valid @RequestBody LoginRequest request) {
		TokenPair tokens = loginService.login(
			request.email(),
			request.password()
		);
		return tokenResponse(tokens, "로그인되었습니다.");
	}

	@PostMapping("/refresh")
	@Operation(summary = "액세스 토큰 재발급")
	public ResponseEntity<ApiResponse<AccessTokenResponse>> refresh(
		@CookieValue(RefreshCookieProvider.REFRESH_TOKEN_KEY)
		String refreshToken
	) {
		TokenPair tokens = loginService.refresh(refreshToken);
		return tokenResponse(tokens, "토큰이 재발급되었습니다.");
	}

	@DeleteMapping("/logout")
	@Operation(summary = "로그아웃")
	public ResponseEntity<Void> logout(
		@CookieValue(value = RefreshCookieProvider.REFRESH_TOKEN_KEY, required = false)
		String refreshToken
	) {
		if (refreshToken != null) {
			loginService.logout(refreshToken);
		}
		ResponseCookie cookie = refreshCookieProvider.expire();
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, cookie.toString())
			.build();
	}

	private ResponseEntity<ApiResponse<AccessTokenResponse>> tokenResponse(TokenPair tokens, String message) {
		ResponseCookie cookie = refreshCookieProvider.create(tokens.refreshToken());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookie.toString())
			.body(ApiResponse.success(
				new AccessTokenResponse(tokens.accessToken()),
				message
			));
	}
}
