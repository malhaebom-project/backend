package com.malhaebom.malhaebom.service.dto;

public record AnswerSubmissionPreparation(
	Long submissionId,
	String processingToken,
	AnswerAssessmentInput assessmentInput,
	AnswerSubmissionResult completedResult
) {

	public AnswerSubmissionPreparation {
		boolean processing = submissionId != null
			&& processingToken != null
			&& assessmentInput != null
			&& completedResult == null;
		boolean completed = submissionId != null
			&& processingToken == null
			&& assessmentInput == null
			&& completedResult != null;
		if (!processing && !completed) {
			throw new IllegalArgumentException(
				"처리 작업 또는 완료 결과 중 하나만 필요합니다."
			);
		}
	}

	public static AnswerSubmissionPreparation processing(
		Long submissionId,
		String processingToken,
		AnswerAssessmentInput assessmentInput
	) {
		return new AnswerSubmissionPreparation(
			submissionId,
			processingToken,
			assessmentInput,
			null
		);
	}

	public static AnswerSubmissionPreparation completed(
		Long submissionId,
		AnswerSubmissionResult completedResult
	) {
		return new AnswerSubmissionPreparation(
			submissionId,
			null,
			null,
			completedResult
		);
	}

	public boolean requiresAssessment() {
		return assessmentInput != null;
	}
}
