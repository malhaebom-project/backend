package com.malhaebom.malhaebom.presentation.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class SubmitAnswerRequestTest {

	private final Validator validator = Validation
		.buildDefaultValidatorFactory()
		.getValidator();

	@Test
	void 음성_답변_ID만_제출_입력으로_받는다() {
		String[] componentNames = Arrays.stream(
			SubmitAnswerRequest.class.getRecordComponents()
		)
			.map(component -> component.getName())
			.toArray(String[]::new);

		assertEquals(1, componentNames.length);
		assertEquals("speechAnswerId", componentNames[0]);
	}

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
