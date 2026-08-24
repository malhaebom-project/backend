package com.malhaebom.malhaebom.global.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class LearningTime {

	private static final ZoneId STUDY_ZONE = ZoneId.of("Asia/Seoul");
	private static final ZoneId STORAGE_ZONE = ZoneOffset.UTC;

	private LearningTime() {
	}

	public static LocalDateTime toStorageStartOfDay(LocalDate date) {
		return date.atStartOfDay(STUDY_ZONE)
			.withZoneSameInstant(STORAGE_ZONE)
			.toLocalDateTime();
	}

	public static LocalDate toStudyDate(LocalDateTime storedAt) {
		return storedAt.atZone(STORAGE_ZONE)
			.withZoneSameInstant(STUDY_ZONE)
			.toLocalDate();
	}

	public static LocalDate currentStudyDate(Clock clock) {
		return LocalDate.now(clock.withZone(STUDY_ZONE));
	}
}
