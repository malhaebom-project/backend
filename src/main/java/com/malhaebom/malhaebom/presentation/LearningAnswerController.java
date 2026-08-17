package com.malhaebom.malhaebom.presentation;

import java.util.concurrent.CompletionStage;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.SubmitAnswerRequest;
import com.malhaebom.malhaebom.presentation.dto.SubmitAnswerResponse;
import com.malhaebom.malhaebom.service.LearningAnswerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/learning-sessions")
@RequiredArgsConstructor
public class LearningAnswerController {

	private final LearningAnswerService learningAnswerService;

	@PostMapping("/{sessionId}/questions/{sessionQuestionId}/answers")
	public CompletionStage<ApiResponse<SubmitAnswerResponse>> submit(
		@PathVariable Long sessionId,
		@PathVariable Long sessionQuestionId,
		@Valid @RequestBody SubmitAnswerRequest request
	) {
		return learningAnswerService.submitAsync(
				sessionId,
				sessionQuestionId,
				request.speechAnswerId()
			)
			.thenApply(submission -> ApiResponse.success(
				SubmitAnswerResponse.from(submission)
			));
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
