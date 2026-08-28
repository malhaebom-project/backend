package com.malhaebom.malhaebom.infra.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiAnswerAssessmentRateLimitPropertiesTest {
	@Test
	void 모든_한도는_양수이고_요청당_토큰은_TPM_이하여야_한다() {
		assertThrows(IllegalArgumentException.class,
			() -> new OpenAiAnswerAssessmentRateLimitProperties(0, 10, 1));
		assertThrows(IllegalArgumentException.class,
			() -> new OpenAiAnswerAssessmentRateLimitProperties(1, 0, 1));
		assertThrows(IllegalArgumentException.class,
			() -> new OpenAiAnswerAssessmentRateLimitProperties(1, 10, 11));
		assertDoesNotThrow(
			() -> new OpenAiAnswerAssessmentRateLimitProperties(
				400, 400_000, 3_000));
	}
}
