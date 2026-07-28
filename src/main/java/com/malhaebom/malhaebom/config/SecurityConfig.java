package com.malhaebom.malhaebom.config;

import com.malhaebom.malhaebom.security.OAuth2FailureHandler;
import com.malhaebom.malhaebom.security.OAuth2SuccessHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        OAuthProperties.class,
        CookieProperties.class
})
public class SecurityConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${spring.security.oauth2.client.registration.google.client-id}")
            String clientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret}")
            String clientSecret
    ) {
        var google = CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("openid", "profile", "email")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }

    @Bean
    @Order(1)
    SecurityFilterChain oauthSecurityFilterChain(
            HttpSecurity http,
            OAuth2SuccessHandler successHandler,
            OAuth2FailureHandler failureHandler
    ) throws Exception {
        http
                .securityMatcher("/oauth2/**", "/login/oauth2/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.IF_REQUIRED
                ))
                .oauth2Login(oauth -> oauth
                        .successHandler(successHandler)
                        .failureHandler(failureHandler));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/learning-topics",
                                "/api/v1/question-types"
                        ).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                ));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtSecretKey(JwtProperties properties) {
        return new SecretKeySpec(
                properties.secret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey secretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        var accessTokenValidator = (org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt>)
                jwt -> "access".equals(jwt.getClaimAsString("token_type"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                "invalid_token",
                                "Access Token만 API 인증에 사용할 수 있습니다.",
                                null
                        ));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                accessTokenValidator
        ));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.isBlank()) {
                return List.of();
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            OAuthProperties properties
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(properties.frontendBaseUrl()));
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PATCH", "DELETE", "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type"
        ));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
