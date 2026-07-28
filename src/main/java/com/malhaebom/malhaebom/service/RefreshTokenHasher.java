package com.malhaebom.malhaebom.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * 불투명 Refresh Token 원문을 저장용 SHA-256 해시로 변환한다.
 *
 * <p>Refresh Token은 충분한 엔트로피를 가진 난수라는 전제에서 단방향
 * SHA-256을 적용하며, 원문은 보관하거나 로그에 기록하지 않는다.</p>
 */
@Component
public class RefreshTokenHasher {

	private static final String SHA_256 = "SHA-256";

	/**
	 * Refresh Token을 SHA-256으로 해시한다.
	 *
	 * @param refreshToken 해시할 Refresh Token 원문
	 * @return 정확히 64자인 lowercase hex 해시
	 * @throws IllegalArgumentException 토큰이 {@code null}이거나 비어 있는 경우
	 */
	public String hash(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new IllegalArgumentException("Refresh token must not be blank.");
		}

		try {
			MessageDigest messageDigest = MessageDigest.getInstance(SHA_256);
			byte[] digest = messageDigest.digest(
				refreshToken.getBytes(StandardCharsets.UTF_8)
			);
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			// SHA-256은 모든 Java 구현의 필수 알고리즘이므로 런타임 구성 오류로 처리한다.
			throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
		}
	}
}
