package com.malhaebom.malhaebom.support;

import java.util.Optional;

import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;

public final class SpeechAnswerTestQueries {

	private SpeechAnswerTestQueries() {
	}

	public static Optional<SpeechAnswer> findByRequestKey(
		SpeechAnswerRepository repository,
		String requestKey
	) {
		return repository.findAll().stream()
			.filter(answer -> requestKey.equals(answer.getRequestKey()))
			.findFirst();
	}
}
