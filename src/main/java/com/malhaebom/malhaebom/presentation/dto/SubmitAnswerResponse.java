package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "비동기 답변 채점 완료 결과")
public record SubmitAnswerResponse(
	@Schema(description = "저장된 학습 답변 ID", example = "101")
	Long answerId,

	@Schema(description = "답변한 학습 세션 문제 ID", example = "12")
	Long sessionQuestionId,

	@Schema(description = "현재 문제의 답변 시도 번호 (1~2)", example = "1", minimum = "1", maximum = "2")
	int attemptNo,

	@Schema(description = "서버에 저장된 STT 답변", example = "It is a cat.")
	String answerText,

	@Schema(description = "채점 결과", example = "CORRECT")
	AnswerResult result,

	@Schema(description = "총점 (0~100)", example = "95", minimum = "0", maximum = "100")
	int score,

	@Schema(description = "문제의 대표 모범 답안", example = "It is a cat.")
	String modelAnswer,

	@Schema(description = "학습자에게 제공할 채점 피드백", example = "정확하게 고양이라고 말했어요!")
	String feedbackText,

	@Schema(description = "피드백 음성 URL. 현재는 제공하지 않으므로 null", nullable = true)
	String feedbackTtsUrl,

	@Schema(description = "현재 문제에 다시 답할 수 있는지 여부", example = "false")
	boolean canRetry,

	@Schema(description = "남은 답변 시도 횟수", example = "0", minimum = "0", maximum = "1")
	int remainingAttempts
) {
	public static SubmitAnswerResponse from(AnswerSubmissionResult submission) {
		return new SubmitAnswerResponse(
			submission.answerId(),
			submission.sessionQuestionId(),
			submission.attemptNo(),
			submission.answerText(),
			submission.result(),
			submission.score(),
			submission.modelAnswer(),
			submission.feedbackText(),
			null,
			submission.canRetry(),
			submission.remainingAttempts()
		);
	}
}
