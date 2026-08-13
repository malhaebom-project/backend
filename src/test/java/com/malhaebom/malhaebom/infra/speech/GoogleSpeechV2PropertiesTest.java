package com.malhaebom.malhaebom.infra.speech;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class GoogleSpeechV2PropertiesTest {

	private final Validator validator = Validation
		.buildDefaultValidatorFactory()
		.getValidator();

	@Test
	void adaptation_boost는_0보다_커야_한다() {
		GoogleSpeechV2Properties properties = properties(0.0f);

		assertThat(validator.validate(properties))
			.extracting(violation -> violation.getPropertyPath().toString())
			.contains("adaptationBoost");
	}

	@Test
	void adaptation_boost는_20을_초과할_수_없다() {
		GoogleSpeechV2Properties properties = properties(20.1f);

		assertThat(validator.validate(properties))
			.extracting(violation -> violation.getPropertyPath().toString())
			.contains("adaptationBoost");
	}

	private GoogleSpeechV2Properties properties(float adaptationBoost) {
		return new GoogleSpeechV2Properties(
			"en-US",
			Duration.ofSeconds(15),
			"global",
			"_",
			"short",
			adaptationBoost
		);
	}
}
