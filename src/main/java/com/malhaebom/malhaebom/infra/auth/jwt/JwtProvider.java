package com.malhaebom.malhaebom.infra.auth.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;

@Component
public class JwtProvider {
	public String createToken(Long userId, Duration duration, SecretKey secretKey) {
		Instant now = Instant.now();

		return Jwts.builder()
			.claims(JwtUserPayload.toClaims(userId))
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(duration)))
			.signWith(secretKey)
			.compact();
	}

	public JwtUserPayload parsePayload(String token, SecretKey secretKey) {
		return JwtUserPayload.from(parseClaims(token, secretKey));
	}

	public boolean isTokenExpired(String token, SecretKey secretKey) {
		try {
			parseClaims(token, secretKey);
			return false;
		} catch (ExpiredJwtException exception) {
			return true;
		}
	}

	private Claims parseClaims(String token, SecretKey secretKey) {
		try {
			return Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
		} catch (ExpiredJwtException exception) {
			throw exception;
		} catch (JwtException | IllegalArgumentException exception) {
			throw new ApiException(
				ErrorCode.UNAUTHORIZED,
				"유효하지 않은 인증 정보입니다.",
				exception
			);
		}
	}
}
