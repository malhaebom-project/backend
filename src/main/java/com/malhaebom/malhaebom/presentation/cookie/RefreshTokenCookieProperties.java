package com.malhaebom.malhaebom.presentation.cookie;

import java.time.Duration;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
