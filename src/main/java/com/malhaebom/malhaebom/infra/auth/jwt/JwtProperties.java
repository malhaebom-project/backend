package com.malhaebom.malhaebom.infra.auth.jwt;

import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(Token access, Token refresh) {
	public record Token(String secretKey, Duration expiration) {
		public SecretKey signingKey() {
			return Keys.hmacShaKeyFor(
				secretKey.getBytes(StandardCharsets.UTF_8)
			);
		}
	}
}
