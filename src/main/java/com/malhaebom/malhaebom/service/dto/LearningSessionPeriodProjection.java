package com.malhaebom.malhaebom.service.dto;

import java.time.LocalDateTime;

public interface LearningSessionPeriodProjection {

	LocalDateTime getStartedAt();

	LocalDateTime getCompletedAt();
}
