package com.malhaebom.malhaebom.service.port;

import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;

public interface SpeechTranscriber {

	String provider();

	SpeechTranscriptionResult transcribe(SpeechAudio audio);
}
