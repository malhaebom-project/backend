package com.malhaebom.malhaebom.domain.learning;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

import com.malhaebom.malhaebom.domain.BaseEntity;

@Entity
@Table(name = "learning_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningSession extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long childId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private LearningTopic topic;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Difficulty difficulty;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LearningSessionStatus status;

	@Column(nullable = false)
	private LocalDateTime startedAt;

	private LocalDateTime completedAt;

	@Embedded
	@Getter(AccessLevel.NONE)
	private LearningSessionQuestions questions = new LearningSessionQuestions();

	public static LearningSession create(
		Long childId,
		LearningTopic topic,
		Difficulty difficulty,
		List<Question> questions
	) {
		validateCreationValues(childId, topic, difficulty);

		LearningSession session = new LearningSession();
		session.childId = childId;
		session.topic = topic;
		session.difficulty = difficulty;
		session.status = LearningSessionStatus.IN_PROGRESS;
		session.startedAt = LocalDateTime.now();
		session.questions.addAll(session, questions);
		return session;
	}

	public LearningSessionQuestion getCurrentQuestion() {
		validateInProgress();
		return questions.getCurrent();
	}

	public void completeCurrentQuestion(boolean correct) {
		validateInProgress();
		questions.completeCurrent(correct);

		if (questions.isCompleted()) {
			complete();
		}
	}

	public void recordWrongAnswerAttempt() {
		validateInProgress();
		questions.recordWrongAnswerAttempt();
	}

	public void useHintOnCurrentQuestion() {
		validateInProgress();
		questions.useHintOnCurrentQuestion();
	}

	public void cancel() {
		validateInProgress();
		status = LearningSessionStatus.CANCELED;
		completedAt = LocalDateTime.now();
	}

	public int getCurrentQuestionIndex() {
		return questions.getCurrentQuestionIndex();
	}

	public int getQuestionCount() {
		return questions.size();
	}

	public int getCorrectCount() {
		return questions.getCorrectCount();
	}

	public boolean isCompleted() {
		return status == LearningSessionStatus.COMPLETED;
	}

	public void complete() {
		validateInProgress();
		markCompleted();
	}

	private void markCompleted() {
		status = LearningSessionStatus.COMPLETED;
		completedAt = LocalDateTime.now();
	}

	private void validateInProgress() {
		if (status != LearningSessionStatus.IN_PROGRESS) {
			throw new IllegalStateException("진행 중인 학습 세션이 아닙니다.");
		}
	}

	private static void validateCreationValues(
		Long childId,
		LearningTopic topic,
		Difficulty difficulty
	) {
		if (childId == null) {
			throw new IllegalArgumentException("어린이 ID는 null일 수 없습니다.");
		}

		if (topic == null) {
			throw new IllegalArgumentException("학습 주제는 null일 수 없습니다.");
		}

		if (difficulty == null) {
			throw new IllegalArgumentException("난이도는 null일 수 없습니다.");
		}
	}
}
