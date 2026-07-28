package com.malhaebom.malhaebom.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<AuthErrorResponse> handle(AuthException exception) {
        AuthErrorCode code = exception.getErrorCode();
        return ResponseEntity.status(code.status()).body(
                new AuthErrorResponse(
                        false,
                        null,
                        code.message(),
                        code.name()
                )
        );
    }

    public record AuthErrorResponse(
            boolean success,
            Object data,
            String message,
            String errorCode
    ) {
    }
}
