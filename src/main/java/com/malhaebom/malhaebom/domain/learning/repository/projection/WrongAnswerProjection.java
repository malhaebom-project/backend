package com.malhaebom.malhaebom.domain.learning.repository.projection;

import java.time.LocalDateTime;

public interface WrongAnswerProjection {

	Long getAnswerId();

	Long getQuestionId();

	String getQuestionText();

	String getImageUrl();

	String getAnswerText();

	String getModelAnswer();

	String getFeedbackText();

	LocalDateTime getAnsweredAt();
}
