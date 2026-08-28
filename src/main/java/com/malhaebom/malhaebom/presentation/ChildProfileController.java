package com.malhaebom.malhaebom.presentation;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.ChildProfileResponse;
import com.malhaebom.malhaebom.presentation.dto.CreateChildProfileRequest;
import com.malhaebom.malhaebom.presentation.dto.UpdateChildProfileRequest;
import com.malhaebom.malhaebom.service.ChildProfileService;
import com.malhaebom.malhaebom.service.dto.ChildProfileResult;
import com.malhaebom.malhaebom.service.dto.LoginUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/children")
@RequiredArgsConstructor
public class ChildProfileController {
	private final ChildProfileService childProfileService;

	@PostMapping
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
	public ApiResponse<List<ChildProfileResponse>> getAll(@Auth LoginUser loginUser) {
		return ApiResponse.success(
			childProfileService.getAll(loginUser.userId()).stream()
				.map(this::toResponse)
				.toList()
		);
	}

	@GetMapping("/{childId}")
	public ApiResponse<ChildProfileResponse> get(@Auth LoginUser loginUser, @PathVariable Long childId) {
		return ApiResponse.success(
			toResponse(childProfileService.get(loginUser.userId(), childId))
		);
	}

	@PatchMapping("/{childId}")
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
	public ApiResponse<Void> delete(@Auth LoginUser loginUser, @PathVariable Long childId) {
		childProfileService.delete(loginUser.userId(), childId);
		return ApiResponse.success(null, "어린이 프로필이 삭제되었습니다.");
	}

	private ChildProfileResponse toResponse(ChildProfileResult result) {
		return ChildProfileResponse.from(result.profile(), result.statistics());
	}
}
