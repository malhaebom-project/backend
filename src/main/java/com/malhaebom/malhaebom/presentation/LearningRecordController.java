package com.malhaebom.malhaebom.presentation;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.LearningHistoryResponse;
import com.malhaebom.malhaebom.presentation.dto.LearningStatisticsResponse;
import com.malhaebom.malhaebom.presentation.dto.WrongAnswerResponse;
import com.malhaebom.malhaebom.service.LearningRecordQueryService;
import com.malhaebom.malhaebom.service.dto.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/children")
@RequiredArgsConstructor
public class LearningRecordController {

	private final LearningRecordQueryService learningRecordQueryService;

	@GetMapping("/{childId}/learning-history")
	public ApiResponse<LearningHistoryResponse> getHistory(
		@Auth LoginUser loginUser,
		@PathVariable Long childId,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "10") int size,
		@RequestParam(required = false)
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
		@RequestParam(required = false)
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
	) {
		return ApiResponse.success(
			LearningHistoryResponse.from(
				learningRecordQueryService.getHistory(
					loginUser.userId(),
					childId,
					page,
					size,
					startDate,
					endDate
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
