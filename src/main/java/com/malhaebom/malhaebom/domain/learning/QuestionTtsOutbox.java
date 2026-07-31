package com.malhaebom.malhaebom.domain.learning;

import com.malhaebom.malhaebom.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "question_tts_outboxes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionTtsOutbox extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long questionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private QuestionTtsOutboxStatus status;

	public static QuestionTtsOutbox create(Long questionId) {
		if (questionId == null) {
			throw new IllegalArgumentException("문제 ID는 null일 수 없습니다.");
		}

		QuestionTtsOutbox outbox = new QuestionTtsOutbox();
		outbox.questionId = questionId;
		outbox.status = QuestionTtsOutboxStatus.PENDING;
		return outbox;
	}

	public void startProcessing() {
		validateStatus(QuestionTtsOutboxStatus.PENDING);
		status = QuestionTtsOutboxStatus.PROCESSING;
	}

	public void complete() {
		validateStatus(QuestionTtsOutboxStatus.PROCESSING);
		status = QuestionTtsOutboxStatus.COMPLETED;
	}

	public void fail() {
		validateStatus(QuestionTtsOutboxStatus.PROCESSING);
		status = QuestionTtsOutboxStatus.FAILED;
	}

	private void validateStatus(QuestionTtsOutboxStatus expected) {
		if (status != expected) {
			throw new IllegalStateException(
				"Outbox 상태가 올바르지 않습니다."
			);
		}
	}
}
