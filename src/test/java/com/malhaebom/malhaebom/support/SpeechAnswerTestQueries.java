package com.malhaebom.malhaebom.support;

import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;

import java.util.Optional;

public final class SpeechAnswerTestQueries {
	private SpeechAnswerTestQueries() {}

	public static Optional<SpeechAnswer> findByRequestKey(SpeechAnswerRepository repository, String requestKey) {
		return repository.findAll().stream()
			.filter(answer -> requestKey.equals(answer.getRequestKey()))
			.findFirst();
	}
}
