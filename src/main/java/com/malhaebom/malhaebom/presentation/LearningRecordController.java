package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.infra.openapi.AuthenticatedErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.ValidationErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.DomainErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.SuccessfulResponse;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.dto.*;
import com.malhaebom.malhaebom.service.LearningRecordQueryService;
import com.malhaebom.malhaebom.service.dto.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/children")
@RequiredArgsConstructor
@Tag(name = "학습 기록", description = "어린이의 학습 이력과 통계를 조회하는 API")
@SecurityRequirement(name = "bearerAuth")
@AuthenticatedErrorResponses
public class LearningRecordController {
	private final LearningRecordQueryService learningRecordQueryService;

	@GetMapping("/{childId}/learning-history")
	@Operation(
		summary = "학습 이력 조회",
		description = """
			완료된 학습 세션을 최신 완료 순서로 조회합니다.

			- `startDate`, `endDate`는 `yyyy-MM-dd` 형식이며 `Asia/Seoul`의 날짜 기준
			- 시작일과 종료일을 모두 포함하며, 한쪽 또는 양쪽 날짜를 생략할 수 있음
			- `startDate`는 `endDate`보다 늦을 수 없음
			- 페이지 번호는 0부터 시작하며 기본값은 `page=0`, `size=10`
			- 응답의 `completedAt`은 UTC ISO 8601 시각(`Z`)으로 반환
			"""
	)
	@SuccessfulResponse(description = "완료된 학습 이력 페이지 조회 성공")
	@ValidationErrorResponses
	@DomainErrorResponses(ErrorCode.CHILD_PROFILE_NOT_FOUND)
	public ApiResponse<LearningHistoryResponse> getHistory(
		@Auth LoginUser loginUser,
		@PathVariable Long childId,
		@Valid @ParameterObject @ModelAttribute LearningHistoryRequest request
	) {
		return ApiResponse.success(
			LearningHistoryResponse.from(
				learningRecordQueryService.getHistory(
					loginUser.userId(),
					childId,
					request.page(),
					request.size(),
					request.startAt(),
					request.endAt()
				)
			)
		);
	}

	@GetMapping("/{childId}/statistics")
	@Operation(summary = "학습 통계 조회")
	@SuccessfulResponse(description = "어린이 누적 학습 통계 조회 성공")
	@DomainErrorResponses(ErrorCode.CHILD_PROFILE_NOT_FOUND)
	public ApiResponse<LearningStatisticsResponse> getStatistics(
		@Auth LoginUser loginUser,
		@PathVariable Long childId
	) {
		return ApiResponse.success(
			LearningStatisticsResponse.from(
				learningRecordQueryService.getStatistics(
					loginUser.userId(),
					childId
				)
			)
		);
	}

	@GetMapping("/{childId}/wrong-answers")
	@Operation(summary = "최근 오답 조회")
	@SuccessfulResponse(description = "최근 오답 목록 조회 성공")
	@DomainErrorResponses(ErrorCode.CHILD_PROFILE_NOT_FOUND)
	public ApiResponse<List<WrongAnswerResponse>> getRecentWrongAnswers(
		@Auth LoginUser loginUser,
		@PathVariable Long childId
	) {
		return ApiResponse.success(
			learningRecordQueryService.getRecentWrongAnswers(
				loginUser.userId(),
				childId
			).stream()
				.map(WrongAnswerResponse::from)
				.toList()
		);
	}
}
