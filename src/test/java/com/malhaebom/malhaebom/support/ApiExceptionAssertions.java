package com.malhaebom.malhaebom.support;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class ApiExceptionAssertions {
	private ApiExceptionAssertions() {}

	public static ApiException assertApiException(ErrorCode expectedErrorCode, Executable executable) {
		ApiException exception = assertThrows(ApiException.class, executable);
		assertEquals(expectedErrorCode, exception.getErrorCode());
		return exception;
	}
}
