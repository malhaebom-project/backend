package com.malhaebom.malhaebom.service.port;

import java.util.List;

import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionTask;

public interface SpeechTranscriber {
	String provider();

	SpeechTranscriptionTask transcribeAsync(
		SpeechAudio audio,
		List<String> adaptationPhrases
	);
}
