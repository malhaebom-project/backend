package com.malhaebom.malhaebom.presentation;

import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.SubmitAnswerRequest;
import com.malhaebom.malhaebom.presentation.dto.SubmitAnswerResponse;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.async.AnswerSubmissionAsyncProperties;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.service.LearningAnswerService;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionTask;
import com.malhaebom.malhaebom.service.dto.LoginUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/learning-sessions")
@RequiredArgsConstructor
public class LearningAnswerController {

	private final LearningAnswerService learningAnswerService;
	private final AnswerSubmissionAsyncProperties asyncProperties;

	@PostMapping("/{sessionId}/questions/{sessionQuestionId}/answers")
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
	public ApiResponse<Void> skipRetry(
		@Auth LoginUser loginUser,
		@PathVariable Long sessionId,
		@PathVariable Long sessionQuestionId
	) {
		learningAnswerService.skipRetry(
			loginUser.userId(),
			sessionId,
			sessionQuestionId
		);
		return ApiResponse.success(null);
	}
}
