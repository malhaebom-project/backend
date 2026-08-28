package com.malhaebom.malhaebom.service.port;

import com.malhaebom.malhaebom.service.dto.TtsAudio;

public interface QuestionTtsStorage {
	String upload(Long questionId, TtsAudio audio);
}
