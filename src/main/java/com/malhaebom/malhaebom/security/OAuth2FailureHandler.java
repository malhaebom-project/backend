package com.malhaebom.malhaebom.security;

import com.malhaebom.malhaebom.config.OAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private final OAuthProperties properties;

    public OAuth2FailureHandler(OAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        String redirectUrl = UriComponentsBuilder
                .fromUriString(properties.frontendBaseUrl())
                .path("/login")
                .queryParam("error", "OAUTH_AUTHENTICATION_FAILED")
                .build()
                .toUriString();
        response.sendRedirect(redirectUrl);
    }
}
