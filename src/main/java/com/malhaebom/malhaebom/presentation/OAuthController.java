package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.config.CookieProperties;
import com.malhaebom.malhaebom.domain.Account;
import com.malhaebom.malhaebom.domain.OAuthProvider;
import com.malhaebom.malhaebom.presentation.dto.AccountResponse;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.OAuthCodeExchangeRequest;
import com.malhaebom.malhaebom.presentation.dto.OAuthCodeExchangeResponse;
import com.malhaebom.malhaebom.service.AuthTokenIssuer;
import com.malhaebom.malhaebom.service.OAuthLoginCodeService;
import com.malhaebom.malhaebom.service.model.IssuedTokens;
import com.malhaebom.malhaebom.service.model.SessionSubject;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthController {

    private final OAuthLoginCodeService loginCodeService;
    private final AuthTokenIssuer tokenIssuer;
    private final CookieProperties cookieProperties;

    public OAuthController(
            OAuthLoginCodeService loginCodeService,
            AuthTokenIssuer tokenIssuer,
            CookieProperties cookieProperties
    ) {
        this.loginCodeService = loginCodeService;
        this.tokenIssuer = tokenIssuer;
        this.cookieProperties = cookieProperties;
    }

    @GetMapping("/{provider}/authorize")
    public ResponseEntity<Void> authorize(
            @PathVariable String provider
    ) {
        OAuthProvider.fromRegistrationId(provider);
        String registrationId = provider.toLowerCase(Locale.ROOT);
        return ResponseEntity.status(302)
                .location(URI.create("/oauth2/authorization/" + registrationId))
                .build();
    }

    @PostMapping("/exchange")
    public ResponseEntity<ApiResponse<OAuthCodeExchangeResponse>> exchange(
            @Valid @RequestBody OAuthCodeExchangeRequest request
    ) {
        Account account = loginCodeService.exchange(request.code());
        IssuedTokens tokens = tokenIssuer.issue(new SessionSubject(
                account.getId(),
                account.getRole()
        ));

        OAuthCodeExchangeResponse body = new OAuthCodeExchangeResponse(
                tokens.accessToken(),
                "Bearer",
                tokens.accessTokenExpiresInSeconds(),
                AccountResponse.from(account)
        );

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookie(tokens).toString()
                )
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(body));
    }

    private ResponseCookie refreshCookie(IssuedTokens tokens) {
        return ResponseCookie
                .from(cookieProperties.name(), tokens.refreshToken())
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path("/api/v1/auth")
                .maxAge(Duration.ofSeconds(
                        tokens.refreshTokenExpiresInSeconds()
                ))
                .build();
    }
}
