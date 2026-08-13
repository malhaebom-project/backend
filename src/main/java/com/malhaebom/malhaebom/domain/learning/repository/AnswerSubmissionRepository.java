package com.malhaebom.malhaebom.domain.learning.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malhaebom.malhaebom.domain.learning.AnswerSubmission;

public interface AnswerSubmissionRepository
	extends JpaRepository<AnswerSubmission, Long> {

	Optional<AnswerSubmission> findBySpeechAnswer_Id(Long speechAnswerId);

	Optional<AnswerSubmission> findBySessionQuestion_IdAndAttemptNo(
		Long sessionQuestionId,
		int attemptNo
	);
}
