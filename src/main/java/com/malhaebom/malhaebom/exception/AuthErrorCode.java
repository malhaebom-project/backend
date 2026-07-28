package com.malhaebom.malhaebom.exception;

import org.springframework.http.HttpStatus;

public enum AuthErrorCode {
    ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "사용할 수 없는 계정입니다."),
    ACCOUNT_LINK_REQUIRED(
            HttpStatus.CONFLICT,
            "기존 이메일 계정에 Google 계정 연결이 필요합니다."
    ),
    OAUTH_EMAIL_NOT_VERIFIED(
            HttpStatus.UNAUTHORIZED,
            "검증되지 않은 Google 이메일입니다."
    ),
    OAUTH_LOGIN_CODE_INVALID(
            HttpStatus.UNAUTHORIZED,
            "유효하지 않은 OAuth 로그인 코드입니다."
    ),
    OAUTH_AUTHENTICATION_FAILED(
            HttpStatus.UNAUTHORIZED,
            "Google 로그인에 실패했습니다."
    );

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
