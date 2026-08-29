package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.infra.openapi.AuthenticatedErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.ValidationErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.DomainErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.SuccessfulResponse;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.ChildProfileResponse;
import com.malhaebom.malhaebom.presentation.dto.CreateChildProfileRequest;
import com.malhaebom.malhaebom.presentation.dto.UpdateChildProfileRequest;
import com.malhaebom.malhaebom.service.ChildProfileService;
import com.malhaebom.malhaebom.service.dto.ChildProfileResult;
import com.malhaebom.malhaebom.service.dto.LoginUser;
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
@RequestMapping("/api/v1/children")
@RequiredArgsConstructor
@Tag(name = "어린이 프로필", description = "어린이 프로필을 관리하는 API")
@SecurityRequirement(name = "bearerAuth")
@AuthenticatedErrorResponses
public class ChildProfileController {
	private final ChildProfileService childProfileService;

	@PostMapping
	@Operation(summary = "어린이 프로필 생성")
	@SuccessfulResponse(status = 201, description = "어린이 프로필 생성 성공")
	@ValidationErrorResponses
	@DomainErrorResponses(ErrorCode.CHILD_NICKNAME_ALREADY_EXISTS)
	public ResponseEntity<ApiResponse<ChildProfileResponse>> create(
		@Auth LoginUser loginUser,
		@Valid @RequestBody CreateChildProfileRequest request
	) {
		ChildProfileResult result = childProfileService.create(
			loginUser.userId(),
			request.nickname(),
			request.age(),
			request.grade(),
			request.level()
		);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(
				toResponse(result),
				"어린이 프로필이 생성되었습니다."
			));
	}

	@GetMapping
	@Operation(summary = "어린이 프로필 목록 조회")
	@SuccessfulResponse(description = "어린이 프로필 목록 조회 성공")
	public ApiResponse<List<ChildProfileResponse>> getAll(@Auth LoginUser loginUser) {
		return ApiResponse.success(
			childProfileService.getAll(loginUser.userId()).stream()
				.map(this::toResponse)
				.toList()
		);
	}

	@GetMapping("/{childId}")
	@Operation(summary = "어린이 프로필 조회")
	@SuccessfulResponse(description = "어린이 프로필 조회 성공")
	@DomainErrorResponses(ErrorCode.CHILD_PROFILE_NOT_FOUND)
	public ApiResponse<ChildProfileResponse> get(@Auth LoginUser loginUser, @PathVariable Long childId) {
		return ApiResponse.success(
			toResponse(childProfileService.get(loginUser.userId(), childId))
		);
	}

	@PatchMapping("/{childId}")
	@Operation(summary = "어린이 프로필 수정")
	@SuccessfulResponse(description = "어린이 프로필 수정 성공")
	@ValidationErrorResponses
	@DomainErrorResponses({
		ErrorCode.CHILD_PROFILE_NOT_FOUND,
		ErrorCode.CHILD_NICKNAME_ALREADY_EXISTS
	})
	public ApiResponse<ChildProfileResponse> update(
		@Auth LoginUser loginUser,
		@PathVariable Long childId,
		@Valid @RequestBody UpdateChildProfileRequest request
	) {
		return ApiResponse.success(
			toResponse(childProfileService.update(
				loginUser.userId(),
				childId,
				request.nickname(),
				request.age(),
				request.grade(),
				request.level()
			)),
			"어린이 프로필이 수정되었습니다."
		);
	}

	@DeleteMapping("/{childId}")
	@Operation(summary = "어린이 프로필 삭제")
	@SuccessfulResponse(description = "어린이 프로필 삭제 성공")
	@DomainErrorResponses(ErrorCode.CHILD_PROFILE_NOT_FOUND)
	public ApiResponse<Void> delete(@Auth LoginUser loginUser, @PathVariable Long childId) {
		childProfileService.delete(loginUser.userId(), childId);
		return ApiResponse.success(null, "어린이 프로필이 삭제되었습니다.");
	}

	private ChildProfileResponse toResponse(ChildProfileResult result) {
		return ChildProfileResponse.from(result.profile(), result.statistics());
	}
}
