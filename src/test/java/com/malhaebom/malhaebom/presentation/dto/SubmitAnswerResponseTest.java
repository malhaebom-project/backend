package com.malhaebom.malhaebom.presentation.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;

class SubmitAnswerResponseTest {

	@Test
	void 제출_결과를_AI_피드백_응답으로_변환한다() {
		AnswerSubmissionResult submission = new AnswerSubmissionResult(
			40L,
			20L,
			1,
			"He is running.",
			AnswerResult.CORRECT,
			100,
			"He is running.",
			"현재진행형을 정확하게 사용했어요!",
			false,
			0
		);

		SubmitAnswerResponse response = SubmitAnswerResponse.from(submission);

		assertEquals(40L, response.answerId());
		assertEquals(20L, response.sessionQuestionId());
		assertEquals(1, response.attemptNo());
		assertEquals("He is running.", response.answerText());
		assertEquals(AnswerResult.CORRECT, response.result());
		assertEquals(100, response.score());
		assertEquals("He is running.", response.modelAnswer());
		assertEquals(
			"현재진행형을 정확하게 사용했어요!",
			response.feedbackText()
		);
		assertNull(response.feedbackTtsUrl());
		assertFalse(response.canRetry());
		assertEquals(0, response.remainingAttempts());
	}
}
