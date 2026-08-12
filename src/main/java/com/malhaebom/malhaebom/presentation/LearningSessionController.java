package com.malhaebom.malhaebom.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.infra.storage.image.QuestionImageUrlResolver;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.dto.CreateLearningSessionRequest;
import com.malhaebom.malhaebom.presentation.dto.CreateLearningSessionResponse;
import com.malhaebom.malhaebom.presentation.dto.LearningSessionResponse;
import com.malhaebom.malhaebom.presentation.dto.NextQuestionResponse;
import com.malhaebom.malhaebom.service.LearningSessionService;
import com.malhaebom.malhaebom.service.dto.LoginUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/learning-sessions")
@RequiredArgsConstructor
public class LearningSessionController {

	private final LearningSessionService learningSessionService;
	private final QuestionImageUrlResolver questionImageUrlResolver;

	@PostMapping
	public ApiResponse<CreateLearningSessionResponse> create(
		@Auth LoginUser loginUser,
		@Valid @RequestBody CreateLearningSessionRequest request
	) {
		return ApiResponse.success(
			CreateLearningSessionResponse.from(
				learningSessionService.create(
					loginUser.userId(),
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
		LearningSessionQuestion sessionQuestion =
			learningSessionService.getNextQuestion(sessionId);
		return ApiResponse.success(
			NextQuestionResponse.from(
				sessionQuestion,
				questionImageUrlResolver.resolve(
					sessionQuestion.getQuestion().getImageUrl()
				)
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
	public ApiResponse<LearningSessionResponse> complete(
		@PathVariable Long sessionId
	) {
		return ApiResponse.success(
			LearningSessionResponse.from(
				learningSessionService.complete(sessionId)
			),
			"학습을 완료했습니다."
		);
	}
}
