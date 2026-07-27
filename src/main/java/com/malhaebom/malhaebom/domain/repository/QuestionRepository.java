package com.malhaebom.malhaebom.domain.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malhaebom.malhaebom.domain.Difficulty;
import com.malhaebom.malhaebom.domain.LearningTopic;
import com.malhaebom.malhaebom.domain.Question;
import com.malhaebom.malhaebom.domain.QuestionType;

public interface QuestionRepository extends JpaRepository<Question, Long> {

	List<Question> findAllByTopicAndDifficultyAndTypeInOrderByIdAsc(
		LearningTopic topic,
		Difficulty difficulty,
		Collection<QuestionType> types
	);
}
