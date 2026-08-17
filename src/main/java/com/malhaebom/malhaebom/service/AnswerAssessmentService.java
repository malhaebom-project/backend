package com.malhaebom.malhaebom.service;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

import org.springframework.stereotype.Service;

import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;
import com.malhaebom.malhaebom.service.port.AnswerAssessmentGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnswerAssessmentService {

	private final AnswerAssessmentGenerator answerAssessmentGenerator;

	public CompletionStage<AnswerAssessment> assessAsync(
		AnswerAssessmentInput input
	) {
		CompletionStage<AnswerAssessment> assessment = Objects.requireNonNull(
			answerAssessmentGenerator.generateAsync(input),
			"AI 평가 작업은 null일 수 없습니다."
		);
		return assessment.thenApply(result -> {
			if (result == null) {
				throw new IllegalStateException("AI 평가 결과가 비어 있습니다.");
			}
			return result;
		});
	}
}
