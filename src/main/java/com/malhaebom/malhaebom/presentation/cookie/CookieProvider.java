package com.malhaebom.malhaebom.presentation.cookie;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CookieProvider {

	public ResponseCookie create(
		String name,
		String value,
		CookieProperties properties,
		Duration ttl
	) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
			.maxAge(ttl)
			.sameSite(properties.getSameSite())
			.secure(properties.isSecure())
			.httpOnly(properties.isHttpOnly())
			.path(properties.getPath());

		if (StringUtils.hasText(properties.getDomain())) {
			builder.domain(properties.getDomain());
		}

		return builder.build();
	}
}
