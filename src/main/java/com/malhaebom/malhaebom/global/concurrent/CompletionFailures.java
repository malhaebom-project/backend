package com.malhaebom.malhaebom.global.concurrent;

import java.util.Objects;
import java.util.concurrent.CompletionException;

public final class CompletionFailures {

	private CompletionFailures() {
	}

	public static Throwable unwrap(Throwable failure) {
		Throwable current = Objects.requireNonNull(
			failure,
			"비동기 작업 실패 예외는 null일 수 없습니다."
		);
		while (current instanceof CompletionException
			&& current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}
}
