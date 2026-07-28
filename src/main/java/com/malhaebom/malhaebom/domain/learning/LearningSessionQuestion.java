package com.malhaebom.malhaebom.domain.learning;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
	name = "learning_session_questions",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_learning_session_question_index",
			columnNames = {"learning_session_id", "question_index"}
		)
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningSessionQuestion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "learning_session_id", nullable = false)
	private LearningSession learningSession;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "question_id", nullable = false)
	private Question question;

	@Column(name = "question_index", nullable = false)
	private int questionIndex;

	@Column(nullable = false)
	private int hintUsedCount;

	@Column(nullable = false)
	private int wrongAnswerCount;

	private Boolean correct;

	private LocalDateTime answeredAt;

	static LearningSessionQuestion create(
		LearningSession learningSession,
		Question question,
		int questionIndex
	) {
		LearningSessionQuestion sessionQuestion = new LearningSessionQuestion();
		sessionQuestion.learningSession = learningSession;
		sessionQuestion.question = question;
		sessionQuestion.questionIndex = questionIndex;
		sessionQuestion.hintUsedCount = 0;
		sessionQuestion.wrongAnswerCount = 0;
		return sessionQuestion;
	}

	void complete(boolean correct) {
		if (isCompleted()) {
			throw new IllegalStateException("이미 완료한 문제입니다.");
		}

		this.correct = correct;
		this.answeredAt = LocalDateTime.now();

		if (!correct) {
			wrongAnswerCount++;
		}
	}

	void recordWrongAnswerAttempt() {
		if (isCompleted()) {
			throw new IllegalStateException("이미 완료한 문제입니다.");
		}

		wrongAnswerCount++;
	}

	void useHint() {
		if (isCompleted()) {
			throw new IllegalStateException("완료한 문제에서는 힌트를 사용할 수 없습니다.");
		}

		hintUsedCount++;
	}

	public boolean isCorrect() {
		return Boolean.TRUE.equals(correct);
	}

	public boolean isCompleted() {
		return answeredAt != null;
	}
}
