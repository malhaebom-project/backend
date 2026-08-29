package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.service.dto.LearningHistory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "학습 이력 페이지")
public record LearningHistoryResponse(
	@Schema(description = "현재 페이지의 완료된 학습 세션 목록")
	List<LearningHistoryItemResponse> content,

	@Schema(description = "0부터 시작하는 현재 페이지 번호", example = "0")
	int page,

	@Schema(description = "페이지당 항목 수", example = "10")
	int size,

	@Schema(description = "검색 조건에 해당하는 전체 항목 수", example = "24")
	long totalElements,

	@Schema(description = "전체 페이지 수", example = "3")
	int totalPages
) {
	public static LearningHistoryResponse from(LearningHistory history) {
		return new LearningHistoryResponse(
			history.content().stream()
				.map(LearningHistoryItemResponse::from)
				.toList(),
			history.page(),
			history.size(),
			history.totalElements(),
			history.totalPages()
		);
	}
}
