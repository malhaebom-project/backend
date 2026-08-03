package com.malhaebom.malhaebom.infra.storage.s3;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.service.dto.TtsAudio;
import com.malhaebom.malhaebom.service.port.QuestionTtsStorage;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
	prefix = "malhaebom.tts",
	name = "enabled",
	havingValue = "true"
)
public class S3QuestionTtsStorage implements QuestionTtsStorage {

	private static final String MP3_EXTENSION = ".mp3";

	private final S3Client s3Client;
	private final AmazonS3Properties properties;

	@Override
	public String upload(Long questionId, TtsAudio audio) {
		String key = createKey(questionId);
		PutObjectRequest request = PutObjectRequest.builder()
			.bucket(properties.bucket())
			.key(key)
			.contentType(audio.contentType())
			.build();

		s3Client.putObject(
			request,
			RequestBody.fromBytes(audio.content())
		);
		return createUrl(key);
	}

	private String createKey(Long questionId) {
		String prefix = properties.keyPrefix();
		if (prefix.endsWith("/")) {
			return prefix + questionId + MP3_EXTENSION;
		}
		return prefix + "/" + questionId + MP3_EXTENSION;
	}

	private String createUrl(String key) {
		String baseUrl = properties.baseUrl();
		if (baseUrl.endsWith("/")) {
			return baseUrl + key;
		}
		return baseUrl + "/" + key;
	}
}
