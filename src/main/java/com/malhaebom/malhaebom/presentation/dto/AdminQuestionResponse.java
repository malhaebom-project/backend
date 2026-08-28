package com.malhaebom.malhaebom.presentation.dto;

import java.time.Instant;
import java.util.Set;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;

public record AdminQuestionResponse(
	Long questionId,
	LearningTopic topic,
	Difficulty difficulty,
	QuestionType type,
	String questionText,
	String questionTextKo,
	String imageUrl,
	String gradingContext,
	String modelAnswer,
	Set<String> acceptedAnswers,
	String hintText,
	String ttsUrl,
	boolean active,
	Instant createdAt,
	Instant updatedAt
) {
	public static AdminQuestionResponse from(Question question, String imageUrl) {
		return new AdminQuestionResponse(
			question.getId(),
			question.getTopic(),
			question.getDifficulty(),
			question.getType(),
			question.getQuestionText(),
			question.getQuestionTextKo(),
			imageUrl,
			question.getGradingContext(),
			question.getModelAnswer(),
			question.getAcceptedAnswers(),
			question.getHintText(),
			question.getTtsUrl(),
			question.isActive(),
			question.getCreatedAt(),
			question.getUpdatedAt()
		);
	}
}
