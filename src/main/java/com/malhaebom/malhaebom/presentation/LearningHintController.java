package com.malhaebom.malhaebom.presentation;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.HintResponse;
import com.malhaebom.malhaebom.service.LearningHintService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/learning-sessions")
@RequiredArgsConstructor
public class LearningHintController {

	private final LearningHintService learningHintService;

	@PostMapping("/{sessionId}/questions/{questionId}/hint")
	public ApiResponse<HintResponse> request(
		@PathVariable Long sessionId,
		@PathVariable Long questionId
	) {
		return ApiResponse.success(
			HintResponse.from(
				learningHintService.request(sessionId, questionId)
			)
		);
	}
}
