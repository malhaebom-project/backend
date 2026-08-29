package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.infra.openapi.AuthenticatedErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.ValidationErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.DomainErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.DomainErrorExample;
import com.malhaebom.malhaebom.infra.openapi.SuccessfulResponse;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
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
	@SuccessfulResponse(description = "학습 세션 생성 및 시작 성공")
	@ValidationErrorResponses
	@DomainErrorResponses({
		ErrorCode.CHILD_PROFILE_NOT_FOUND,
		ErrorCode.LEARNING_TOPIC_NOT_FOUND,
		ErrorCode.INSUFFICIENT_QUESTIONS
	})
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
	@SuccessfulResponse(description = "현재 진행할 문제 조회 성공")
	@DomainErrorResponses({
		ErrorCode.LEARNING_SESSION_NOT_FOUND,
		ErrorCode.CHILD_PROFILE_NOT_FOUND,
		ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS
	})
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
	@SuccessfulResponse(description = "학습 세션 진행 상태 조회 성공")
	@DomainErrorResponses({
		ErrorCode.LEARNING_SESSION_NOT_FOUND,
		ErrorCode.CHILD_PROFILE_NOT_FOUND
	})
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
	@SuccessfulResponse(description = "학습 세션 완료 결과 조회 성공")
	@DomainErrorResponses(
		value = {
			ErrorCode.LEARNING_SESSION_NOT_FOUND,
			ErrorCode.CHILD_PROFILE_NOT_FOUND
		},
		examples = @DomainErrorExample(
			code = ErrorCode.INVALID_REQUEST,
			message = "모든 문제를 완료한 학습 세션이 아닙니다.",
			name = "SESSION_NOT_COMPLETED"
		)
	)
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
