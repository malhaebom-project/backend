package com.malhaebom.malhaebom.presentation;

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
public class LearningHintController {
	private final LearningHintService learningHintService;

	@PostMapping("/{sessionId}/questions/{questionId}/hint")
	@Operation(summary = "힌트 요청")
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
