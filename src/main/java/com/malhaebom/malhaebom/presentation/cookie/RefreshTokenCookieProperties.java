package com.malhaebom.malhaebom.presentation.cookie;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@ConfigurationProperties(prefix = "cookie.refresh-token")
public class RefreshTokenCookieProperties extends CookieProperties {
	private final Duration ttl;

	public RefreshTokenCookieProperties(
		String path,
		String domain,
		String sameSite,
		boolean secure,
		boolean httpOnly,
		Duration ttl
	) {
		super(path, domain, sameSite, secure, httpOnly);
		this.ttl = ttl;
	}
}
