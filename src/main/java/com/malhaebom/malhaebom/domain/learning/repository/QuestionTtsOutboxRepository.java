package com.malhaebom.malhaebom.domain.learning.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malhaebom.malhaebom.domain.learning.QuestionTtsOutbox;

public interface QuestionTtsOutboxRepository
	extends JpaRepository<QuestionTtsOutbox, Long> {
}
