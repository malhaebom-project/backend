package com.malhaebom.malhaebom.service;

import org.springframework.stereotype.Service;

import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;
import com.malhaebom.malhaebom.service.port.AnswerAssessmentGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnswerAssessmentService {

	private final AnswerAssessmentGenerator answerAssessmentGenerator;

	public AnswerAssessment assess(AnswerAssessmentInput input) {
		AnswerAssessment assessment = answerAssessmentGenerator.generate(input);
		if (assessment == null) {
			throw new IllegalStateException("AI 평가 결과가 비어 있습니다.");
		}

		return assessment;
	}
}
