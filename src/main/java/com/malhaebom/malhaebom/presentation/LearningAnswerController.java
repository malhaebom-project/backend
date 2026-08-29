package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.openapi.AuthenticatedErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.AnswerSubmissionErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.ValidationErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.DomainErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.DomainErrorExample;
import com.malhaebom.malhaebom.infra.async.AnswerSubmissionAsyncProperties;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.SubmitAnswerRequest;
import com.malhaebom.malhaebom.presentation.dto.SubmitAnswerResponse;
import com.malhaebom.malhaebom.service.LearningAnswerRetryService;
import com.malhaebom.malhaebom.service.LearningAnswerService;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionTask;
import com.malhaebom.malhaebom.service.dto.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping("/api/v1/learning-sessions")
@RequiredArgsConstructor
@Tag(name = "학습 세션")
@SecurityRequirement(name = "bearerAuth")
@AuthenticatedErrorResponses
public class LearningAnswerController {
	private final LearningAnswerService learningAnswerService;
	private final LearningAnswerRetryService learningAnswerRetryService;
	private final AnswerSubmissionAsyncProperties asyncProperties;

	@PostMapping("/{sessionId}/questions/{sessionQuestionId}/answers")
	@Operation(
		summary = "답변 제출",
		description = """
			업로드가 완료된 음성 답변을 채점하며, 완료될 때까지 같은 HTTP 요청으로 기다립니다.

			- 별도 작업 ID 조회나 polling API를 사용하지 않음
			- 서버 처리 제한시간은 25초, HTTP 요청 제한시간은 30초
			- HTTP 제한시간 초과 시 `504 ANSWER_SUBMISSION_TIMEOUT`
			- 채점 과부하 시 `503 ANSWER_ASSESSMENT_OVERLOADED`
			- 프론트엔드는 위 과부하 오류에만 최대 2회 재시도하며 1~2초, 3~5초 간격을 사용
			- 동일 제출 재요청은 서버의 제출 예약 상태를 재사용하여 중복 채점을 방지
			"""
	)
	@ValidationErrorResponses
	@AnswerSubmissionErrorResponses
	@DomainErrorResponses(
		value = {
			ErrorCode.CURRENT_QUESTION_MISMATCH,
			ErrorCode.CHILD_PROFILE_NOT_FOUND,
			ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS
		},
		examples = {
			@DomainErrorExample(code = ErrorCode.INVALID_REQUEST, message = "이미 답변 제출에 사용된 음성 답변입니다.", name = "SPEECH_ANSWER_ALREADY_USED"),
			@DomainErrorExample(code = ErrorCode.INVALID_REQUEST, message = "처리가 완료되지 않은 음성 답변입니다.", name = "SPEECH_ANSWER_NOT_COMPLETED"),
			@DomainErrorExample(code = ErrorCode.INVALID_REQUEST, message = "답변 가능 횟수를 초과했습니다.", name = "ANSWER_ATTEMPT_LIMIT_EXCEEDED"),
			@DomainErrorExample(code = ErrorCode.ANSWER_SUBMISSION_PROCESSING, message = "답변 제출 처리 권한이 만료되었습니다.", name = "ANSWER_SUBMISSION_LEASE_EXPIRED")
		}
	)
	public DeferredResult<ApiResponse<SubmitAnswerResponse>> submit(
		@Auth LoginUser loginUser,
		@PathVariable Long sessionId,
		@PathVariable Long sessionQuestionId,
		@Valid @RequestBody SubmitAnswerRequest request
	) {
		AnswerSubmissionTask submission = learningAnswerService.submitAsync(
			loginUser.userId(),
			sessionId,
			sessionQuestionId,
			request.speechAnswerId()
		);
		DeferredResult<ApiResponse<SubmitAnswerResponse>> response =
			new DeferredResult<>(asyncProperties.requestTimeout().toMillis());

		submission.result().whenComplete((result, exception) -> {
			if (exception != null) {
				response.setErrorResult(exception);
				return;
			}
			response.setResult(ApiResponse.success(
				SubmitAnswerResponse.from(result)
			));
		});
		response.onTimeout(() -> {
			submission.cancel();
			response.setErrorResult(new ApiException(
				ErrorCode.ANSWER_SUBMISSION_TIMEOUT
			));
		});
		return response;
	}

	@PostMapping("/{sessionId}/questions/{sessionQuestionId}/skip-retry")
	@Operation(
		summary = "재시도 건너뛰기",
		description = "오답 재도전 기회가 남아 있을 때 재시도를 포기하고 다음 문제로 진행합니다. 이미 진행 중인 제출이 있으면 `409 ANSWER_SUBMISSION_CONFLICT`를 반환합니다."
	)
	@ValidationErrorResponses
	@DomainErrorResponses(
		value = {
			ErrorCode.LEARNING_SESSION_NOT_FOUND,
			ErrorCode.CHILD_PROFILE_NOT_FOUND,
			ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS,
			ErrorCode.CURRENT_QUESTION_MISMATCH,
			ErrorCode.ANSWER_SUBMISSION_CONFLICT
		},
		examples = {
			@DomainErrorExample(code = ErrorCode.INVALID_REQUEST, message = "오답 제출 후에만 재시도를 건너뛸 수 있습니다.", name = "WRONG_ANSWER_REQUIRED"),
			@DomainErrorExample(code = ErrorCode.INVALID_REQUEST, message = "재시도 가능한 오답이 아닙니다.", name = "ANSWER_NOT_RETRYABLE")
		}
	)
	public ApiResponse<Void> skipRetry(
		@Auth LoginUser loginUser,
		@PathVariable Long sessionId,
		@PathVariable Long sessionQuestionId
	) {
		learningAnswerRetryService.skipRetry(loginUser.userId(), sessionId, sessionQuestionId);
		return ApiResponse.success(null);
	}
}
