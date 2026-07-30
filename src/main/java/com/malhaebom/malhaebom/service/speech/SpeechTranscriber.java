package com.malhaebom.malhaebom.service.speech;

import com.malhaebom.malhaebom.service.model.SpeechAudio;
import com.malhaebom.malhaebom.service.model.SpeechTranscriptionResult;

public interface SpeechTranscriber {

	SpeechTranscriptionResult transcribe(
		Long speechAnswerId,
		String requestKey,
		SpeechAudio audio
	);
}
