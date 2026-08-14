package com.malhaebom.malhaebom.service.dto;

import java.util.Objects;

public sealed interface AnswerSubmissionPreparation
	permits AnswerSubmissionPreparation.Processing,
	AnswerSubmissionPreparation.Completed {

	Long submissionId();

	static Processing processing(
		Long submissionId,
		String processingToken,
		AnswerAssessmentInput assessmentInput
	) {
		return new Processing(
			submissionId,
			processingToken,
			assessmentInput
		);
	}

	static Completed completed(
		Long submissionId,
		AnswerSubmissionResult result
	) {
		return new Completed(submissionId, result);
	}

	record Processing(
		Long submissionId,
		String processingToken,
		AnswerAssessmentInput assessmentInput
	) implements AnswerSubmissionPreparation {

		public Processing {
			Objects.requireNonNull(submissionId, "답변 제출 예약 ID는 null일 수 없습니다.");
			if (processingToken == null || processingToken.isBlank()) {
				throw new IllegalArgumentException("처리 토큰은 비어 있을 수 없습니다.");
			}
			Objects.requireNonNull(assessmentInput, "채점 입력은 null일 수 없습니다.");
		}
	}

	record Completed(
		Long submissionId,
		AnswerSubmissionResult result
	) implements AnswerSubmissionPreparation {

		public Completed {
			Objects.requireNonNull(submissionId, "답변 제출 예약 ID는 null일 수 없습니다.");
			Objects.requireNonNull(result, "완료된 답변 결과는 null일 수 없습니다.");
		}
	}
}
