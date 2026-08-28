package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.infra.openapi.AuthenticatedErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.ValidationErrorResponses;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.dto.AdminQuestionRequest;
import com.malhaebom.malhaebom.presentation.dto.AdminQuestionResponse;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.service.AdminQuestionService;
import com.malhaebom.malhaebom.service.dto.LoginUser;
import com.malhaebom.malhaebom.service.port.QuestionImageUrlResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/questions")
@RequiredArgsConstructor
@Tag(name = "관리자", description = "관리자가 학습 문제를 관리하는 API")
@SecurityRequirement(name = "bearerAuth")
@AuthenticatedErrorResponses
public class AdminQuestionController {
	private final AdminQuestionService adminQuestionService;
	private final QuestionImageUrlResolver questionImageUrlResolver;

	@PostMapping
	@Operation(summary = "문제 등록")
	@ValidationErrorResponses
	public ResponseEntity<ApiResponse<AdminQuestionResponse>> create(
		@Auth LoginUser loginUser,
		@Valid @RequestBody AdminQuestionRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(
				toResponse(
					adminQuestionService.create(
						loginUser.userId(),
						request.toCommand()
					)
				)
			));
	}

	@GetMapping
	@Operation(summary = "전체 문제 조회")
	public ApiResponse<List<AdminQuestionResponse>> getAll(@Auth LoginUser loginUser) {
		List<AdminQuestionResponse> questions =
			adminQuestionService.getAll(loginUser.userId())
				.stream()
				.map(this::toResponse)
				.toList();
		return ApiResponse.success(questions);
	}

	@GetMapping("/{questionId}")
	@Operation(summary = "문제 상세 조회")
	public ApiResponse<AdminQuestionResponse> get(@Auth LoginUser loginUser, @PathVariable Long questionId) {
		return ApiResponse.success(
			toResponse(
				adminQuestionService.get(
					loginUser.userId(),
					questionId
				)
			)
		);
	}

	@PutMapping("/{questionId}")
	@Operation(summary = "문제 수정")
	@ValidationErrorResponses
	public ApiResponse<AdminQuestionResponse> update(
		@Auth LoginUser loginUser,
		@PathVariable Long questionId,
		@Valid @RequestBody AdminQuestionRequest request
	) {
		return ApiResponse.success(
			toResponse(
				adminQuestionService.update(
					loginUser.userId(),
					questionId,
					request.toCommand()
				)
			)
		);
	}

	@DeleteMapping("/{questionId}")
	@Operation(summary = "문제 삭제")
	public ApiResponse<Void> delete(@Auth LoginUser loginUser, @PathVariable Long questionId) {
		adminQuestionService.delete(loginUser.userId(), questionId);
		return ApiResponse.success(null, "문제를 삭제했습니다.");
	}

	private AdminQuestionResponse toResponse(Question question) {
		return AdminQuestionResponse.from(
			question,
			questionImageUrlResolver.resolve(question.getImageUrl())
		);
	}
}
