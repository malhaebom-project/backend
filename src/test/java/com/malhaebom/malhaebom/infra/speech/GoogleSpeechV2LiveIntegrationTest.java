package com.malhaebom.malhaebom.infra.speech;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

@SpringBootTest
@ActiveProfiles({"test", "local"})
@EnabledIfEnvironmentVariable(
	named = "GOOGLE_STT_LIVE_TEST",
	matches = "true"
)
class GoogleSpeechV2LiveIntegrationTest {

	@Autowired
	private SpeechTranscriber transcriber;

	@Test
	@Timeout(30)
	void 실제_Google_Cloud_STT_V2로_샘플_음성을_변환한다()
		throws IOException {
		Path sampleAudio = Path.of(
			requiredEnvironment("GOOGLE_STT_SAMPLE_AUDIO")
		);
		assertThat(sampleAudio).isRegularFile();

		SpeechTranscriptionResult result = transcriber.transcribe(
			new SpeechAudio(
				Files.readAllBytes(sampleAudio),
				"audio/mpeg"
			)
		);

		assertThat(result.transcript()).isNotBlank();
		assertThat(result.provider()).isEqualTo(
			GoogleSpeechV2Transcriber.PROVIDER
		);
		System.out.printf(
			"LIVE_STT_TRANSCRIPT=%s%nLIVE_STT_CONFIDENCE=%s%n",
			result.transcript(),
			result.confidence()
		);
	}

	private String requiredEnvironment(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " 환경변수가 필요합니다.");
		}
		return value;
	}
}
