package com.malhaebom.malhaebom.domain.learning.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionStatus;
import com.malhaebom.malhaebom.service.dto.ChildStatisticsProjection;
import com.malhaebom.malhaebom.service.dto.LearningHistoryProjection;

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

	@Query("""
		select session.childId as childId,
			count(distinct session.id) as totalStudyCount,
			coalesce(sum(case when sessionQuestion.correct = true then 1 else 0 end), 0) as correctCount,
			count(sessionQuestion.id) as questionCount
		from LearningSession session
		join session.questions.values sessionQuestion
		where session.childId in :childIds
			and session.status = :status
		group by session.childId
		""")
	List<ChildStatisticsProjection> findChildStatistics(
		@Param("childIds") List<Long> childIds,
		@Param("status") LearningSessionStatus status
	);

	default List<ChildStatisticsProjection> findChildStatistics(List<Long> childIds) {
		return findChildStatistics(childIds, LearningSessionStatus.COMPLETED);
	}

	@Query(
		value = """
			select session.id as sessionId,
				session.topic as topic,
				session.difficulty as difficulty,
				count(sessionQuestion.id) as questionCount,
				coalesce(sum(case when sessionQuestion.correct = true then 1 else 0 end), 0) as correctCount,
				session.startedAt as startedAt,
				session.completedAt as completedAt
			from LearningSession session
			join session.questions.values sessionQuestion
			where session.childId = :childId
				and session.status = :status
				and session.completedAt >= :startAt
				and session.completedAt < :endAt
			group by session.id,
				session.topic,
				session.difficulty,
				session.startedAt,
				session.completedAt
			order by session.completedAt desc, session.id desc
			""",
		countQuery = """
			select count(session.id)
			from LearningSession session
			where session.childId = :childId
				and session.status = :status
				and session.completedAt >= :startAt
				and session.completedAt < :endAt
			"""
	)
	Page<LearningHistoryProjection> findLearningHistory(
		@Param("childId") Long childId,
		@Param("status") LearningSessionStatus status,
		@Param("startAt") LocalDateTime startAt,
		@Param("endAt") LocalDateTime endAt,
		Pageable pageable
	);
}
