package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.openapi.AuthenticatedErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.ValidationErrorResponses;
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
	@Operation(summary = "답변 제출")
	@ValidationErrorResponses
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
	@Operation(summary = "재시도 건너뛰기")
	@ValidationErrorResponses
	public ApiResponse<Void> skipRetry(
		@Auth LoginUser loginUser,
		@PathVariable Long sessionId,
		@PathVariable Long sessionQuestionId
	) {
		learningAnswerRetryService.skipRetry(loginUser.userId(), sessionId, sessionQuestionId);
		return ApiResponse.success(null);
	}
}
