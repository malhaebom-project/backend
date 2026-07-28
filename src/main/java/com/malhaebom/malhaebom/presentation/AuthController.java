package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.config.CookieProperties;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.service.RefreshTokenService;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RefreshTokenService refreshTokenService;
    private final CookieProperties cookieProperties;

    public AuthController(
            RefreshTokenService refreshTokenService,
            CookieProperties cookieProperties
    ) {
        this.refreshTokenService = refreshTokenService;
        this.cookieProperties = cookieProperties;
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(
                    name = "${app.cookie.name}",
                    required = false
            ) String refreshToken
    ) {
        refreshTokenService.revoke(refreshToken);

        ResponseCookie expiredCookie = ResponseCookie
                .from(cookieProperties.name(), "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(null));
    }
}
