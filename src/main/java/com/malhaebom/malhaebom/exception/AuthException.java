package com.malhaebom.malhaebom.exception;

public class AuthException extends RuntimeException {

    private final AuthErrorCode errorCode;

    public AuthException(AuthErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public AuthErrorCode getErrorCode() {
        return errorCode;
    }
}
