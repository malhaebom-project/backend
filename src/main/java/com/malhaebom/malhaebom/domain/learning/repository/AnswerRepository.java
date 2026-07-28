package com.malhaebom.malhaebom.domain.learning.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malhaebom.malhaebom.domain.learning.Answer;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

	Optional<Answer> findFirstBySessionQuestion_IdOrderByAttemptNoDesc(
		Long sessionQuestionId
	);
}
