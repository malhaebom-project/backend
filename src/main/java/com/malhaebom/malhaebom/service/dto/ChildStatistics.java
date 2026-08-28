package com.malhaebom.malhaebom.service.dto;

public record ChildStatistics(long totalStudyCount, long correctCount, long questionCount) {
	public static ChildStatistics empty() {
		return new ChildStatistics(0, 0, 0);
	}

	public double totalCorrectRate() {
		if (questionCount == 0) {
			return 0.0;
		}
		return Math.round(correctCount * 1000.0 / questionCount) / 10.0;
	}
}
