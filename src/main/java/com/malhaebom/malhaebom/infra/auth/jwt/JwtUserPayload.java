package com.malhaebom.malhaebom.infra.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import java.time.Instant;

public record JwtUserPayload(Long userId, Instant expiresAt) {
	private static final String USER_ID_KEY = "userId";

	public static JwtUserPayload from(Claims claims) {
		return new JwtUserPayload(
			claims.get(USER_ID_KEY, Long.class),
			claims.getExpiration().toInstant()
		);
	}

	public static Claims toClaims(Long userId) {
		return Jwts.claims()
			.add(USER_ID_KEY, userId)
			.build();
	}
}
