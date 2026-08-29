package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.infra.openapi.ValidationErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.DomainErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.DomainErrorExample;
import com.malhaebom.malhaebom.infra.openapi.SuccessfulResponse;
import com.malhaebom.malhaebom.infra.openapi.RefreshCookieResponse;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.presentation.cookie.RefreshCookieProvider;
import com.malhaebom.malhaebom.presentation.dto.*;
import com.malhaebom.malhaebom.service.LoginService;
import com.malhaebom.malhaebom.service.UserService;
import com.malhaebom.malhaebom.service.dto.TokenPair;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
	@SuccessfulResponse(status = 201, description = "보호자 회원가입 성공")
	@ValidationErrorResponses
	@DomainErrorResponses(ErrorCode.EMAIL_ALREADY_EXISTS)
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
	@Operation(
		summary = "로그인",
		description = "응답 본문으로 액세스 토큰을 반환하고 Refresh Token은 HttpOnly 쿠키로 발급합니다. "
			+ "브라우저 클라이언트는 이후 요청에 쿠키가 포함되도록 credentials 옵션을 사용해야 합니다."
	)
	@SuccessfulResponse(description = "로그인 및 액세스 토큰 발급 성공")
	@RefreshCookieResponse
	@ValidationErrorResponses
	@DomainErrorResponses(examples = @DomainErrorExample(
		code = ErrorCode.UNAUTHORIZED,
		message = "이메일 또는 비밀번호가 올바르지 않습니다.",
		name = "INVALID_CREDENTIALS"
	))
	public ResponseEntity<ApiResponse<AccessTokenResponse>> login(@Valid @RequestBody LoginRequest request) {
		TokenPair tokens = loginService.login(
			request.email(),
			request.password()
		);
		return tokenResponse(tokens, "로그인되었습니다.");
	}

	@PostMapping("/refresh")
	@Operation(
		summary = "액세스 토큰 재발급",
		description = "브라우저가 전송한 Refresh Token 쿠키를 검증하고 액세스 토큰을 재발급합니다. "
			+ "성공하면 Refresh Token도 회전되어 같은 이름의 쿠키로 다시 설정됩니다."
	)
	@SuccessfulResponse(description = "액세스 토큰 재발급 성공")
	@RefreshCookieResponse
	@DomainErrorResponses(examples = {
		@DomainErrorExample(
			code = ErrorCode.UNAUTHORIZED,
			message = "리프레시 토큰이 유효하지 않습니다.",
			name = "INVALID_REFRESH_TOKEN"
		),
		@DomainErrorExample(
			code = ErrorCode.NOT_FOUND,
			message = "존재하지 않는 로그인 세션입니다.",
			name = "LOGIN_SESSION_NOT_FOUND"
		)
	})
	public ResponseEntity<ApiResponse<AccessTokenResponse>> refresh(
		@Parameter(
			name = RefreshCookieProvider.REFRESH_TOKEN_KEY,
			description = "로그인 시 발급된 HttpOnly Refresh Token 쿠키. 브라우저가 자동으로 전송하며 재발급 성공 시 회전됩니다.",
			required = true,
			in = ParameterIn.COOKIE,
			example = "<refresh-token>"
		)
		@CookieValue(RefreshCookieProvider.REFRESH_TOKEN_KEY)
		String refreshToken
	) {
		TokenPair tokens = loginService.refresh(refreshToken);
		return tokenResponse(tokens, "토큰이 재발급되었습니다.");
	}

	@DeleteMapping("/logout")
	@Operation(
		summary = "로그아웃",
		description = "Refresh Token 쿠키가 있으면 서버의 로그인 세션을 삭제하고, "
			+ "쿠키 존재 여부와 관계없이 만료된 Set-Cookie 헤더를 반환합니다."
	)
	@SuccessfulResponse(status = 204, description = "로그아웃 및 Refresh Token 쿠키 만료 성공")
	@RefreshCookieResponse(RefreshCookieResponse.Action.EXPIRE)
	@DomainErrorResponses(examples = @DomainErrorExample(
		code = ErrorCode.NOT_FOUND,
		message = "존재하지 않는 로그인 세션입니다.",
		name = "LOGIN_SESSION_NOT_FOUND"
	))
	public ResponseEntity<Void> logout(
		@Parameter(
			name = RefreshCookieProvider.REFRESH_TOKEN_KEY,
			description = "선택적인 HttpOnly Refresh Token 쿠키. 없더라도 로그아웃 응답과 쿠키 만료 처리는 동일합니다.",
			required = false,
			in = ParameterIn.COOKIE,
			example = "<refresh-token>"
		)
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
