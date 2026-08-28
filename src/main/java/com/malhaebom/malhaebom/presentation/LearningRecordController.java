package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.dto.*;
import com.malhaebom.malhaebom.service.LearningRecordQueryService;
import com.malhaebom.malhaebom.service.dto.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/children")
@RequiredArgsConstructor
public class LearningRecordController {
	private final LearningRecordQueryService learningRecordQueryService;

	@GetMapping("/{childId}/learning-history")
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
