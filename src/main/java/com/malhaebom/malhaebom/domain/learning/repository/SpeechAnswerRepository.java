package com.malhaebom.malhaebom.domain.learning.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;

import jakarta.persistence.LockModeType;

public interface SpeechAnswerRepository extends JpaRepository<SpeechAnswer, Long> {

	Optional<SpeechAnswer> findByRequestKey(String requestKey);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select speechAnswer from SpeechAnswer speechAnswer where speechAnswer.id = :id")
	Optional<SpeechAnswer> findForUpdateById(@Param("id") Long id);

	Optional<SpeechAnswer> findFirstBySessionQuestion_IdOrderByRecordingNoDesc(
		Long sessionQuestionId
	);
}
