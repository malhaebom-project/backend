package com.malhaebom.malhaebom.infra.speech;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "google.stt")
public record GoogleSpeechV2Properties(
	@NotBlank String languageCode,
	@NotNull Duration timeout,
	@NotBlank String location,
	@NotBlank String recognizerId,
	@NotBlank String model,
	@DecimalMin(value = "0.0", inclusive = false) @DecimalMax("20.0")
	float adaptationBoost
) {

	public GoogleSpeechV2Properties {
		if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
			throw new IllegalArgumentException(
				"STT 타임아웃은 0초보다 커야 합니다."
			);
		}
	}
}
