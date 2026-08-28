package com.malhaebom.malhaebom.presentation.cookie;

import lombok.Getter;

@Getter
public class CookieProperties {

	private final String path;
	private final String domain;
	private final String sameSite;
	private final boolean secure;
	private final boolean httpOnly;

	public CookieProperties(
		String path,
		String domain,
		String sameSite,
		boolean secure,
		boolean httpOnly
	) {
		this.path = path;
		this.domain = domain;
		this.sameSite = sameSite;
		this.secure = secure;
		this.httpOnly = httpOnly;
	}

}
