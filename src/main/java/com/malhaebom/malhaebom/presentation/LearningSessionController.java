package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.infra.openapi.AuthenticatedErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.ValidationErrorResponses;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.dto.*;
import com.malhaebom.malhaebom.service.LearningSessionService;
import com.malhaebom.malhaebom.service.dto.LoginUser;
import com.malhaebom.malhaebom.service.port.QuestionImageUrlResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/learning-sessions")
@RequiredArgsConstructor
@Tag(name = "학습 세션", description = "학습을 시작하고 진행 상태를 관리하는 API")
@SecurityRequirement(name = "bearerAuth")
@AuthenticatedErrorResponses
public class LearningSessionController {
	private final LearningSessionService learningSessionService;
	private final QuestionImageUrlResolver questionImageUrlResolver;

	@PostMapping
	@Operation(summary = "학습 시작")
	@ValidationErrorResponses
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
	@Operation(summary = "다음 문제 조회")
	public ApiResponse<NextQuestionResponse> getNextQuestion(
		@Auth LoginUser loginUser,
		@PathVariable Long sessionId
	) {
		LearningSessionQuestion sessionQuestion =
			learningSessionService.getNextQuestion(loginUser.userId(), sessionId);
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
	@Operation(summary = "학습 세션 조회")
	public ApiResponse<LearningSessionResponse> get(
		@Auth LoginUser loginUser,
		@PathVariable Long sessionId
	) {
		return ApiResponse.success(
			LearningSessionResponse.from(learningSessionService.get(
				loginUser.userId(),
				sessionId
			))
		);
	}

	@PostMapping("/{sessionId}/complete")
	@Operation(summary = "학습 완료")
	public ApiResponse<LearningSessionResultResponse> complete(
		@Auth LoginUser loginUser,
		@PathVariable Long sessionId
	) {
		return ApiResponse.success(
			LearningSessionResultResponse.from(
				learningSessionService.complete(loginUser.userId(), sessionId)
			),
			"학습을 완료했습니다."
		);
	}
}
