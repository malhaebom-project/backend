package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.dto.*;
import com.malhaebom.malhaebom.service.LearningRecordQueryService;
import com.malhaebom.malhaebom.service.dto.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/children")
@RequiredArgsConstructor
@Tag(name = "학습 기록", description = "어린이의 학습 이력과 통계를 조회하는 API")
@SecurityRequirement(name = "bearerAuth")
public class LearningRecordController {
	private final LearningRecordQueryService learningRecordQueryService;

	@GetMapping("/{childId}/learning-history")
	@Operation(summary = "학습 이력 조회")
	public ApiResponse<LearningHistoryResponse> getHistory(
		@Auth LoginUser loginUser,
		@PathVariable Long childId,
		@Valid @ModelAttribute LearningHistoryRequest request
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
