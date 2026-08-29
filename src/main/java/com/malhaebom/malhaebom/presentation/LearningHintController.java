package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.infra.openapi.AuthenticatedErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.ValidationErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.DomainErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.DomainErrorExample;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.HintResponse;
import com.malhaebom.malhaebom.service.LearningHintService;
import com.malhaebom.malhaebom.service.dto.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning-sessions")
@RequiredArgsConstructor
@Tag(name = "학습 세션")
@SecurityRequirement(name = "bearerAuth")
@AuthenticatedErrorResponses
public class LearningHintController {
	private final LearningHintService learningHintService;

	@PostMapping("/{sessionId}/questions/{questionId}/hint")
	@Operation(summary = "힌트 요청")
	@ValidationErrorResponses
	@DomainErrorResponses(
		value = {
			ErrorCode.LEARNING_SESSION_NOT_FOUND,
			ErrorCode.CHILD_PROFILE_NOT_FOUND,
			ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS,
			ErrorCode.CURRENT_QUESTION_MISMATCH
		},
		examples = @DomainErrorExample(
			code = ErrorCode.INVALID_REQUEST,
			message = "현재 문제에 등록된 힌트가 없습니다.",
			name = "HINT_NOT_AVAILABLE"
		)
	)
	public ApiResponse<HintResponse> request(
		@Auth LoginUser loginUser,
		@PathVariable Long sessionId,
		@PathVariable Long questionId
	) {
		return ApiResponse.success(
				HintResponse.from(
						learningHintService.request(loginUser.userId(), sessionId, questionId)
				)
		);
	}
}
