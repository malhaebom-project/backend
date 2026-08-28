package com.malhaebom.malhaebom.service.dto;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnswerAssessmentTest {
	@Test
	void 세부_점수의_합계와_정답_결과를_계산한다() {
		AnswerAssessment assessment = new AnswerAssessment(
			true,
			45,
			25,
			15,
			"현재진행형을 정확하게 사용했어요!"
		);

		assertEquals(85, assessment.totalScore());
		assertEquals(AnswerResult.CORRECT, assessment.result());
	}

	@Test
	void 총점이_80점_미만이면_부분_정답이다() {
		AnswerAssessment assessment = new AnswerAssessment(
			true,
			45,
			20,
			14,
			"동작 표현은 좋았어요. 주어를 함께 말해 보세요."
		);

		assertEquals(79, assessment.totalScore());
		assertEquals(AnswerResult.PARTIALLY_CORRECT, assessment.result());
	}

	@Test
	void 총점이_높아도_의미_점수가_30점_미만이면_오답이다() {
		AnswerAssessment assessment = new AnswerAssessment(
			true,
			25,
			28,
			20,
			"문법은 맞지만 질문의 핵심 의미와 다른 답변이에요."
		);

		assertEquals(73, assessment.totalScore());
		assertEquals(AnswerResult.INCORRECT, assessment.result());
	}

	@Test
	void 피드백을_정규화한다() {
		AnswerAssessment assessment = new AnswerAssessment(
			true,
			20,
			10,
			5,
			"  좋은 시도예요. 문장 구조를 다시 확인해 보세요.  "
		);

		assertEquals(
			"좋은 시도예요. 문장 구조를 다시 확인해 보세요.",
			assessment.feedbackText()
		);
		assertEquals(AnswerResult.INCORRECT, assessment.result());
	}

	@Test
	void 인식되지_않은_답변은_최종_결과도_인식_실패다() {
		AnswerAssessment assessment = new AnswerAssessment(
			false,
			0,
			0,
			0,
			"답변을 인식하지 못했어요. 다시 한번 말해 주세요."
		);

		assertEquals(0, assessment.totalScore());
		assertEquals(AnswerResult.UNRECOGNIZED, assessment.result());
	}

	@Test
	void 세부_점수가_허용_범위를_벗어나면_거부한다() {
		assertThrows(
			IllegalArgumentException.class,
			() -> new AnswerAssessment(
				true,
				51,
				0,
				0,
				"피드백"
			)
		);
	}

	@Test
	void 인식되지_않은_답변에_점수가_있으면_거부한다() {
		assertThrows(
			IllegalArgumentException.class,
			() -> new AnswerAssessment(
				false,
				1,
				0,
				0,
				"피드백"
			)
		);
	}
}
