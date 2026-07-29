package com.malhaebom.malhaebom.domain.learning.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.malhaebom.malhaebom.domain.learning.LearningSession;

import jakarta.persistence.LockModeType;

public interface LearningSessionRepository extends JpaRepository<LearningSession, Long> {

	@Query("""
		select distinct session
		from LearningSession session
		left join fetch session.questions.values sessionQuestion
		left join fetch sessionQuestion.question
		where session.id = :sessionId
		""")
	Optional<LearningSession> findWithQuestionsById(@Param("sessionId") Long sessionId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select session
		from LearningSession session
		where session.id = :sessionId
		""")
	Optional<LearningSession> findForUpdateById(
		@Param("sessionId") Long sessionId
	);
}
