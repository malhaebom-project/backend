package com.malhaebom.malhaebom.presentation.auth;

import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;

@Component
public class HeaderProvider {

	private static final String BEARER_PREFIX = "Bearer ";

	public String extractAccessToken(String authorizationHeader) {
		if (
			authorizationHeader != null
				&& authorizationHeader.startsWith(BEARER_PREFIX)
		) {
			return authorizationHeader.substring(BEARER_PREFIX.length()).trim();
		}

		throw new ApiException(
			ErrorCode.UNAUTHORIZED,
			"유효하지 않은 인증 정보입니다."
		);
	}
}
