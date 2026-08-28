package com.malhaebom.malhaebom.infra.tts;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.malhaebom.malhaebom.service.dto.TtsAudio;
import com.malhaebom.malhaebom.service.port.TtsClient;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
	prefix = "malhaebom.tts",
	name = "enabled",
	havingValue = "true"
)
public class GoogleTextToSpeechClient implements TtsClient {
	private static final String MP3_CONTENT_TYPE = "audio/mpeg";

	private final TextToSpeechClient client;
	private final GoogleTextToSpeechProperties properties;

	@Override
	public TtsAudio generate(String text) {
		SynthesisInput input = SynthesisInput.newBuilder()
			.setText(text)
			.build();
		VoiceSelectionParams.Builder voice =
			VoiceSelectionParams.newBuilder()
				.setLanguageCode(properties.languageCode())
				.setName(properties.voiceName());

		AudioConfig audioConfig = AudioConfig.newBuilder()
			.setAudioEncoding(AudioEncoding.MP3)
			.setSpeakingRate(properties.speakingRate())
			.setPitch(properties.pitch())
			.build();

		SynthesizeSpeechResponse response = client.synthesizeSpeech(
			input,
			voice.build(),
			audioConfig
		);

		return new TtsAudio(
			response.getAudioContent().toByteArray(),
			MP3_CONTENT_TYPE
		);
	}
}
