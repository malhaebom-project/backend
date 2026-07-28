package com.malhaebom.malhaebom.presentation.cookie;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieProvider {

	public ResponseCookie create(
		String name,
		String value,
		CookieProperties properties,
		Duration ttl
	) {
		return ResponseCookie.from(name, value)
			.maxAge(ttl)
			.domain(properties.getDomain())
			.sameSite(properties.getSameSite())
			.secure(properties.isSecure())
			.httpOnly(properties.isHttpOnly())
			.path(properties.getPath())
			.build();
	}
}
