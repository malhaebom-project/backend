package com.malhaebom.malhaebom.presentation;

import java.util.concurrent.CompletionException;

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
import com.malhaebom.malhaebom.service.LearningAnswerService;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionTask;

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
		@PathVariable Long sessionId,
		@PathVariable Long sessionQuestionId,
		@Valid @RequestBody SubmitAnswerRequest request
	) {
		AnswerSubmissionTask submission = learningAnswerService.submitAsync(
			sessionId,
			sessionQuestionId,
			request.speechAnswerId()
		);
		DeferredResult<ApiResponse<SubmitAnswerResponse>> response =
			new DeferredResult<>(asyncProperties.requestTimeout().toMillis());

		submission.result().whenComplete((result, exception) -> {
			if (exception != null) {
				response.setErrorResult(unwrapCompletionException(exception));
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

	private Throwable unwrapCompletionException(Throwable exception) {
		Throwable cause = exception;
		while (cause instanceof CompletionException
			&& cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}

	@PostMapping("/{sessionId}/questions/{sessionQuestionId}/skip-retry")
	public ApiResponse<Void> skipRetry(
		@PathVariable Long sessionId,
		@PathVariable Long sessionQuestionId
	) {
		learningAnswerService.skipRetry(sessionId, sessionQuestionId);
		return ApiResponse.success(null);
	}
}
