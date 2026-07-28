package com.malhaebom.malhaebom.presentation.cookie;

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

	public String getPath() {
		return path;
	}

	public String getDomain() {
		return domain;
	}

	public String getSameSite() {
		return sameSite;
	}

	public boolean isSecure() {
		return secure;
	}

	public boolean isHttpOnly() {
		return httpOnly;
	}
}
