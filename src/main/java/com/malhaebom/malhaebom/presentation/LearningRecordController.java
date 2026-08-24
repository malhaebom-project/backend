package com.malhaebom.malhaebom.presentation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.LearningHistoryRequest;
import com.malhaebom.malhaebom.presentation.dto.LearningHistoryResponse;
import com.malhaebom.malhaebom.presentation.dto.LearningStatisticsResponse;
import com.malhaebom.malhaebom.presentation.dto.WrongAnswerResponse;
import com.malhaebom.malhaebom.service.LearningRecordQueryService;
import com.malhaebom.malhaebom.service.dto.LoginUser;

import jakarta.validation.Valid;
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
