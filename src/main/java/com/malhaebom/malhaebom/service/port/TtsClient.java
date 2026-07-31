package com.malhaebom.malhaebom.service.port;

import com.malhaebom.malhaebom.service.dto.TtsAudio;

public interface TtsClient {

	TtsAudio generate(String text);
}
