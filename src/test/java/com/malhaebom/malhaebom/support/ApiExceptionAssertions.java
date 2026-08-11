package com.malhaebom.malhaebom.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.function.Executable;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;

public final class ApiExceptionAssertions {

	private ApiExceptionAssertions() {
	}

	public static ApiException assertApiException(
		ErrorCode expectedErrorCode,
		Executable executable
	) {
		ApiException exception = assertThrows(
			ApiException.class,
			executable
		);
		assertEquals(expectedErrorCode, exception.getErrorCode());
		return exception;
	}
}
