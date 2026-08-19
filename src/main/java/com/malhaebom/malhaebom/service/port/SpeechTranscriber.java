package com.malhaebom.malhaebom.service.port;

import java.util.List;
import java.util.concurrent.CompletionException;

import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionTask;

public interface SpeechTranscriber {

	String provider();

	SpeechTranscriptionTask transcribeAsync(
		SpeechAudio audio,
		List<String> adaptationPhrases
	);

	default SpeechTranscriptionResult transcribe(
		SpeechAudio audio,
		List<String> adaptationPhrases
	) {
		try {
			return transcribeAsync(audio, adaptationPhrases)
				.result()
				.toCompletableFuture()
				.join();
		} catch (CompletionException exception) {
			if (exception.getCause() instanceof RuntimeException cause) {
				throw cause;
			}
			throw exception;
		}
	}
}
