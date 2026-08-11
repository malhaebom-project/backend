package com.malhaebom.malhaebom.presentation.dto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class SubmitAnswerRequestTest {

	private final Validator validator = Validation
		.buildDefaultValidatorFactory()
		.getValidator();

	@Test
	void 음성_답변_ID는_필수다() {
		assertTrue(
			validator.validate(new SubmitAnswerRequest(null))
				.stream()
				.anyMatch(violation -> violation
					.getPropertyPath()
					.toString()
					.equals("speechAnswerId"))
		);
	}
}
