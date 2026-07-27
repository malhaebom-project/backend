package com.malhaebom.malhaebom.service.model;

import java.util.Objects;

/**
 * 토큰 담당자 B가 로그인 흐름에 반환하는 발급 결과다.
 *
 * <p>refreshToken은 HTTP 응답 본문이 아니라 HttpOnly 쿠키로 전달해야 한다.</p>
 */
public record IssuedTokens(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds,
        long refreshTokenExpiresInSeconds
) {

    public IssuedTokens {
        requireText(accessToken, "accessToken");
        requireText(refreshToken, "refreshToken");

        if (accessTokenExpiresInSeconds <= 0) {
            throw new IllegalArgumentException(
                    "accessTokenExpiresInSeconds는 0보다 커야 합니다."
            );
        }
        if (refreshTokenExpiresInSeconds <= 0) {
            throw new IllegalArgumentException(
                    "refreshTokenExpiresInSeconds는 0보다 커야 합니다."
            );
        }
    }

    private static void requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "은 필수입니다.");
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "은 비어 있을 수 없습니다."
            );
        }
    }
}
