package com.malhaebom.malhaebom.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.CompleteLearningSessionResponse;
import com.malhaebom.malhaebom.presentation.dto.CreateLearningSessionRequest;
import com.malhaebom.malhaebom.presentation.dto.CreateLearningSessionResponse;
import com.malhaebom.malhaebom.presentation.dto.LearningSessionResponse;
import com.malhaebom.malhaebom.presentation.dto.NextQuestionResponse;
import com.malhaebom.malhaebom.service.LearningSessionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/learning-sessions")
@RequiredArgsConstructor
public class LearningSessionController {

	private final LearningSessionService learningSessionService;

	@PostMapping
	public ApiResponse<CreateLearningSessionResponse> create(
		@Valid @RequestBody CreateLearningSessionRequest request
	) {
		return ApiResponse.success(
			CreateLearningSessionResponse.from(
				learningSessionService.create(
					request.childId(),
					request.topicId(),
					request.difficulty(),
					request.questionTypes(),
					request.questionCount()
				)
			),
			"학습을 시작합니다."
		);
	}

	@GetMapping("/{sessionId}/questions/next")
	public ApiResponse<NextQuestionResponse> getNextQuestion(
		@PathVariable Long sessionId
	) {
		return ApiResponse.success(
			NextQuestionResponse.from(
				learningSessionService.getNextQuestion(sessionId)
			)
		);
	}

	@GetMapping("/{sessionId}")
	public ApiResponse<LearningSessionResponse> get(
		@PathVariable Long sessionId
	) {
		return ApiResponse.success(
			LearningSessionResponse.from(learningSessionService.get(sessionId))
		);
	}

	@PostMapping("/{sessionId}/complete")
	public ApiResponse<CompleteLearningSessionResponse> complete(
		@PathVariable Long sessionId
	) {
		return ApiResponse.success(
			CompleteLearningSessionResponse.from(
				learningSessionService.complete(sessionId)
			),
			"학습을 완료했습니다."
		);
	}
}
