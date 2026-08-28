package com.malhaebom.malhaebom.service.port;

import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionTask;

import java.util.List;

public interface SpeechTranscriber {
	String provider();

	SpeechTranscriptionTask transcribeAsync(
		SpeechAudio audio,
		List<String> adaptationPhrases
	);
}
