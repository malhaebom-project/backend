package com.malhaebom.malhaebom.service.dto;

import java.util.List;

public record LearningHistory(
	List<LearningHistoryItem> content,
	int page,
	int size,
	long totalElements,
	int totalPages
) {
}
