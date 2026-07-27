package com.malhaebom.malhaebom.domain.learning.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;

public interface QuestionRepository extends JpaRepository<Question, Long> {

	List<Question> findAllByTopicAndDifficultyAndTypeInOrderByIdAsc(
		LearningTopic topic,
		Difficulty difficulty,
		Collection<QuestionType> types
	);
}
