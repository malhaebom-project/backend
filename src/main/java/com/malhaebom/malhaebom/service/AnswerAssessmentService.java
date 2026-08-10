package com.malhaebom.malhaebom.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.malhaebom.malhaebom.domain.learning.AnswerEvaluation;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.port.AnswerAssessmentGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnswerAssessmentService {

	private static final Logger log = LoggerFactory.getLogger(
		AnswerAssessmentService.class
	);

	private final AnswerAssessmentGenerator answerAssessmentGenerator;
	private final AnswerEvaluator answerEvaluator;

	public AnswerAssessment assess(Question question, String answerText) {
		try {
			AnswerAssessment assessment = answerAssessmentGenerator.generate(
				question,
				answerText
			);
			if (assessment == null) {
				throw new IllegalStateException("AI 평가 결과가 비어 있습니다.");
			}

			return assessment;
		} catch (RuntimeException exception) {
			log.warn(
				"AI 답변 평가에 실패해 기본 평가를 사용합니다. errorType={}",
				exception.getClass().getSimpleName()
			);
			AnswerEvaluation fallback = answerEvaluator.evaluate(
				question,
				answerText
			);
			return AnswerAssessment.fallback(fallback);
		}
	}
}
