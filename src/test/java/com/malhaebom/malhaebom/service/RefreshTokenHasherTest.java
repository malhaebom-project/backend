package com.malhaebom.malhaebom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RefreshTokenHasherTest {

	private final RefreshTokenHasher refreshTokenHasher = new RefreshTokenHasher();

	@Test
	void 알려진_입력의_SHA256_lowercase_hex를_반환한다() {
		String hash = refreshTokenHasher.hash("abc");

		assertThat(hash).isEqualTo(
			"ba7816bf8f01cfea414140de5dae2223"
				+ "b00361a396177a9cb410ff61f20015ad"
		);
		assertThat(hash).matches("[0-9a-f]{64}");
	}

	@Test
	void 같은_토큰은_항상_같은_해시를_만든다() {
		String refreshToken = "same-opaque-refresh-token";

		assertThat(refreshTokenHasher.hash(refreshToken))
			.isEqualTo(refreshTokenHasher.hash(refreshToken));
	}

	@Test
	void 다른_토큰은_다른_해시를_만든다() {
		assertThat(refreshTokenHasher.hash("first-opaque-refresh-token"))
			.isNotEqualTo(refreshTokenHasher.hash("second-opaque-refresh-token"));
	}

	@Test
	void null과_빈_토큰을_거부한다() {
		assertThatThrownBy(() -> refreshTokenHasher.hash(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Refresh token must not be blank.");

		assertThatThrownBy(() -> refreshTokenHasher.hash(""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Refresh token must not be blank.");

		assertThatThrownBy(() -> refreshTokenHasher.hash(" "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Refresh token must not be blank.");
	}
}
