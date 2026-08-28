package com.malhaebom.malhaebom.infra.speech;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GoogleSpeechRateLimitPropertiesTest {
	@Test
	void 분당_요청_한도는_1_이상이어야_한다() {
		assertThrows(
			IllegalArgumentException.class,
			() -> new GoogleSpeechRateLimitProperties(0)
		);
		assertDoesNotThrow(() -> new GoogleSpeechRateLimitProperties(240));
	}
}
