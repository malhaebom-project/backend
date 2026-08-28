package com.malhaebom.malhaebom.presentation.dto;

import java.util.List;

import com.malhaebom.malhaebom.service.dto.LearningHistory;

public record LearningHistoryResponse(
	List<LearningHistoryItemResponse> content,
	int page,
	int size,
	long totalElements,
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
