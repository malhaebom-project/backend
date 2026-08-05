package com.malhaebom.malhaebom.presentation;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.infra.storage.image.QuestionImageUrlResolver;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.dto.AdminQuestionRequest;
import com.malhaebom.malhaebom.presentation.dto.AdminQuestionResponse;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.service.AdminQuestionService;
import com.malhaebom.malhaebom.service.dto.LoginUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionController {

	private final AdminQuestionService adminQuestionService;
	private final QuestionImageUrlResolver questionImageUrlResolver;

	@PostMapping
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
	public ApiResponse<List<AdminQuestionResponse>> getAll(
		@Auth LoginUser loginUser
	) {
		List<AdminQuestionResponse> questions =
			adminQuestionService.getAll(loginUser.userId())
				.stream()
				.map(this::toResponse)
				.toList();
		return ApiResponse.success(questions);
	}

	@GetMapping("/{questionId}")
	public ApiResponse<AdminQuestionResponse> get(
		@Auth LoginUser loginUser,
		@PathVariable Long questionId
	) {
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
	public ApiResponse<Void> delete(
		@Auth LoginUser loginUser,
		@PathVariable Long questionId
	) {
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
