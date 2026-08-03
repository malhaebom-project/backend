package com.malhaebom.malhaebom.service.port;

import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;

public interface SpeechTranscriber {

	SpeechTranscriptionResult transcribe(
		Long speechAnswerId,
		String requestKey,
		SpeechAudio audio
	);
}
