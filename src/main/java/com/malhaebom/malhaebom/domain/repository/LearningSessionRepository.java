package com.malhaebom.malhaebom.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.malhaebom.malhaebom.domain.LearningSession;

public interface LearningSessionRepository extends JpaRepository<LearningSession, Long> {

	@Query("""
		select distinct session
		from LearningSession session
		left join fetch session.questions.values sessionQuestion
		left join fetch sessionQuestion.question
		where session.id = :sessionId
		""")
	Optional<LearningSession> findWithQuestionsById(@Param("sessionId") Long sessionId);
}
