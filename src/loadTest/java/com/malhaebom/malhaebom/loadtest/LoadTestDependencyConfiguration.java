package com.malhaebom.malhaebom.loadtest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionTask;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

@Configuration(proxyBeanMethods = false)
public class LoadTestDependencyConfiguration {
	@Bean
	SpeechTranscriber loadTestSpeechTranscriber() {
		return new SpeechTranscriber() {
			@Override
			public String provider() {
				return "LOAD_TEST_DISABLED";
			}

			@Override
			public SpeechTranscriptionTask transcribeAsync(
				SpeechAudio audio,
				java.util.List<String> adaptationPhrases
			) {
				return SpeechTranscriptionTask.failed(
					new UnsupportedOperationException(
						"fixture 도구에서는 STT를 사용할 수 없습니다."
					)
				);
			}
		};
	}
}
