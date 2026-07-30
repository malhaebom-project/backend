package com.malhaebom.malhaebom.domain.learning.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;

public interface SpeechAnswerRepository extends JpaRepository<SpeechAnswer, Long> {

	Optional<SpeechAnswer> findByRequestKey(String requestKey);

	Optional<SpeechAnswer> findFirstBySessionQuestion_IdOrderByRecordingNoDesc(
		Long sessionQuestionId
	);
}
