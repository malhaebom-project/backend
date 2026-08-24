package com.malhaebom.malhaebom.domain.learning.repository.projection;

import java.time.LocalDateTime;

public interface LearningSessionPeriodProjection {

	LocalDateTime getStartedAt();

	LocalDateTime getCompletedAt();
}
