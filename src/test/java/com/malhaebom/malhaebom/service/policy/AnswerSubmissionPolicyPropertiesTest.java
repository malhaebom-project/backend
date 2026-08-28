package com.malhaebom.malhaebom.service.policy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AnswerSubmissionPolicyPropertiesTest {
	@Test
	void 시간_설정은_1초_이상이어야_한다() {
		assertThatThrownBy(() -> new AnswerSubmissionPolicyProperties(
			Duration.ofMillis(999),
			Duration.ofSeconds(60)
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 처리_임대_시간은_작업_기한보다_길어야_한다() {
		assertThatThrownBy(() -> new AnswerSubmissionPolicyProperties(
			Duration.ofSeconds(25),
			Duration.ofSeconds(25)
		)).isInstanceOf(IllegalArgumentException.class);
	}
}
