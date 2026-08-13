package com.malhaebom.malhaebom.domain.learning.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.malhaebom.malhaebom.domain.learning.AnswerSubmission;
import com.malhaebom.malhaebom.domain.learning.AnswerSubmissionStatus;

import jakarta.persistence.LockModeType;

public interface AnswerSubmissionRepository
	extends JpaRepository<AnswerSubmission, Long> {

	Optional<AnswerSubmission> findBySpeechAnswer_Id(Long speechAnswerId);

	boolean existsBySessionQuestion_IdAndStatusIn(
		Long sessionQuestionId,
		Collection<AnswerSubmissionStatus> statuses
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select submission
		from AnswerSubmission submission
		where submission.id = :id
		""")
	Optional<AnswerSubmission> findForUpdateById(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select submission
		from AnswerSubmission submission
		where submission.speechAnswer.id = :speechAnswerId
		""")
	Optional<AnswerSubmission> findForUpdateBySpeechAnswer_Id(
		@Param("speechAnswerId") Long speechAnswerId
	);

	Optional<AnswerSubmission> findBySessionQuestion_IdAndAttemptNo(
		Long sessionQuestionId,
		int attemptNo
	);

	Optional<AnswerSubmission> findFirstBySessionQuestion_IdOrderByAttemptNoDesc(
		Long sessionQuestionId
	);
}
