package com.malhaebom.malhaebom.infra.ai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AnswerAssessmentQueuePropertiesTest {
	@Test
	void queue_capacity는_0_이상이어야_한다() {
		assertThrows(IllegalArgumentException.class,
			() -> new AnswerAssessmentQueueProperties(
				-1, Duration.ofSeconds(10)));
	}

	@Test
	void queue가_있으면_max_wait는_0보다_커야_한다() {
		assertThrows(IllegalArgumentException.class,
			() -> new AnswerAssessmentQueueProperties(64, Duration.ZERO));
		assertThrows(IllegalArgumentException.class,
			() -> new AnswerAssessmentQueueProperties(64, null));
	}

	@Test
	void queue_capacity_0은_대기_시간_없이_fail_fast를_지원한다() {
		assertDoesNotThrow(() -> new AnswerAssessmentQueueProperties(
			0, Duration.ZERO));
	}
}
