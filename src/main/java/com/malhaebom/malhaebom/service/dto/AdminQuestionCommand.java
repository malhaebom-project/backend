package com.malhaebom.malhaebom.service.dto;

import java.util.Set;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.QuestionType;

public record AdminQuestionCommand(
	LearningTopic topic,
	Difficulty difficulty,
	QuestionType type,
	String questionText,
	String questionTextKo,
	String imageUrl,
	String gradingContext,
	String modelAnswer,
	Set<String> acceptedAnswers,
	String hintText
) {
}
