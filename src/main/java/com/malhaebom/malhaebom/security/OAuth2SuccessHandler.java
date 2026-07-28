package com.malhaebom.malhaebom.security;

import com.malhaebom.malhaebom.config.OAuthProperties;
import com.malhaebom.malhaebom.domain.Account;
import com.malhaebom.malhaebom.domain.OAuthProvider;
import com.malhaebom.malhaebom.exception.AuthException;
import com.malhaebom.malhaebom.service.OAuthAccountService;
import com.malhaebom.malhaebom.service.OAuthLoginCodeService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthAccountService accountService;
    private final OAuthLoginCodeService loginCodeService;
    private final OAuthProperties properties;

    public OAuth2SuccessHandler(
            OAuthAccountService accountService,
            OAuthLoginCodeService loginCodeService,
            OAuthProperties properties
    ) {
        this.accountService = accountService;
        this.loginCodeService = loginCodeService;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        try {
            OAuth2AuthenticationToken oauthToken =
                    (OAuth2AuthenticationToken) authentication;
            OidcUser oidcUser = (OidcUser) oauthToken.getPrincipal();
            OAuthProvider provider = OAuthProvider.fromRegistrationId(
                    oauthToken.getAuthorizedClientRegistrationId()
            );

            Account account = accountService.resolveOrCreate(provider, oidcUser);
            String loginCode = loginCodeService.issue(account.getId());
            clearTemporarySession(request);

            String redirectUrl = UriComponentsBuilder
                    .fromUriString(properties.frontendBaseUrl())
                    .path("/oauth/callback")
                    .queryParam("code", loginCode)
                    .build()
                    .toUriString();
            response.sendRedirect(redirectUrl);
        } catch (AuthException exception) {
            clearTemporarySession(request);
            response.sendRedirect(errorRedirect(exception.getErrorCode().name()));
        }
    }

    private String errorRedirect(String errorCode) {
        return UriComponentsBuilder
                .fromUriString(properties.frontendBaseUrl())
                .path("/login")
                .queryParam("error", errorCode)
                .build()
                .toUriString();
    }

    private void clearTemporarySession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
