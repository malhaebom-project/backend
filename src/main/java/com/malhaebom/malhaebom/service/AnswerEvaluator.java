package com.malhaebom.malhaebom.service;

import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.domain.learning.AnswerEvaluation;
import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.Question;

@Component
public class AnswerEvaluator {

	public AnswerEvaluation evaluate(Question question, String answerText) {
		if (question == null) {
			throw new IllegalArgumentException("문제는 null일 수 없습니다.");
		}

		if (answerText == null || answerText.isBlank()) {
			throw new IllegalArgumentException("답변은 비어 있을 수 없습니다.");
		}

		AnswerResult result = question.matchesAnswer(answerText)
			? AnswerResult.CORRECT
			: AnswerResult.INCORRECT;
		return AnswerEvaluation.from(result);
	}
}
