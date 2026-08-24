package com.malhaebom.malhaebom.presentation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.malhaebom.malhaebom.global.time.LearningTime;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record LearningHistoryRequest(
	@PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다.")
	Integer page,

	@Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
	@Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
	Integer size,

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate startDate,

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	LocalDate endDate
) {
	private static final LocalDateTime DEFAULT_START_AT =
		LearningTime.toStorageStartOfDay(LocalDate.of(1970, 1, 1));
	private static final LocalDateTime DEFAULT_END_AT =
		LearningTime.toStorageStartOfDay(LocalDate.of(9999, 1, 1));

	public LearningHistoryRequest {
		if (page == null) {
			page = 0;
		}
		if (size == null) {
			size = 10;
		}
	}

	@AssertTrue(message = "시작일은 종료일보다 늦을 수 없습니다.")
	public boolean isDateRangeValid() {
		return startDate == null
			|| endDate == null
			|| !startDate.isAfter(endDate);
	}

	public LocalDateTime startAt() {
		if (startDate == null) {
			return DEFAULT_START_AT;
		}
		return LearningTime.toStorageStartOfDay(startDate);
	}

	public LocalDateTime endAt() {
		if (endDate == null) {
			return DEFAULT_END_AT;
		}
		return LearningTime.toStorageStartOfDay(endDate.plusDays(1));
	}
}
