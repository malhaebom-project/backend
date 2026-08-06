package com.malhaebom.malhaebom.infra.speech;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "malhaebom.stt")
public record GoogleSpeechV2Properties(
	@NotBlank String languageCode,
	@NotNull Duration timeout,
	@Valid @NotNull Google google
) {

	public GoogleSpeechV2Properties {
		if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
			throw new IllegalArgumentException(
				"STT 타임아웃은 0초보다 커야 합니다."
			);
		}
	}

	public record Google(
		@NotBlank String projectId,
		@NotBlank String location,
		@NotBlank String recognizerId,
		@NotBlank String model,
		@Valid @NotNull Credentials credentials
	) {
	}

	public record Credentials(
		@NotNull Resource location
	) {
	}
}
