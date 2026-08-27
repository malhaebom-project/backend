package com.malhaebom.malhaebom.domain.learning;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

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
		session.startedAt = LocalDateTime.now(ZoneOffset.UTC);
		session.questions.addAll(session, questions);
		return session;
	}

	public LearningSessionQuestion getCurrentQuestion() {
		validateInProgress();
		return questions.getCurrent();
	}

	public AnswerSubmission reserveAnswerSubmission(
		Long sessionQuestionId,
		SpeechAnswer speechAnswer,
		int attemptNo
	) {
		validateAnswerSubmissionTarget(sessionQuestionId);
		return AnswerSubmission.reserve(
			questions.getCurrent(),
			speechAnswer,
			attemptNo
		);
	}

	public void ensureCanReserveAnswerSubmission(Long sessionQuestionId) {
		validateAnswerSubmissionTarget(sessionQuestionId);
	}

	public void applyAnswerResult(Answer answer) {
		Objects.requireNonNull(answer, "답변은 null일 수 없습니다.");
		validateAnswerSubmissionTarget(answer.getSessionQuestion().getId());

		if (AnswerAttemptPolicy.canRetry(answer)) {
			questions.recordWrongAnswerAttempt();
			return;
		}

		questions.completeCurrent(answer.isCorrect());
		if (questions.isCompleted()) {
			complete();
		}
	}

	public void ensureCanProcess(AnswerSubmission submission) {
		Objects.requireNonNull(
			submission,
			"답변 제출 예약은 null일 수 없습니다."
		);
		ensureCanReserveAnswerSubmission(submission.getSessionQuestion().getId());
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

	public void skipRetryOnCurrentQuestion() {
		validateInProgress();
		questions.skipRetryOnCurrentQuestion();

		if (questions.isCompleted()) {
			complete();
		}
	}

	public void useHintOnCurrentQuestion() {
		validateInProgress();
		questions.useHintOnCurrentQuestion();
	}

	public void cancel() {
		validateInProgress();
		status = LearningSessionStatus.CANCELED;
		completedAt = LocalDateTime.now(ZoneOffset.UTC);
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

	public boolean isInProgress() {
		return status == LearningSessionStatus.IN_PROGRESS;
	}

	public Duration getStudyDuration() {
		if (!isCompleted() || completedAt == null) {
			throw new IllegalStateException(
				"완료된 학습 세션만 학습 시간을 계산할 수 있습니다."
			);
		}
		if (completedAt.isBefore(startedAt)) {
			throw new IllegalStateException(
				"학습 완료 시각은 시작 시각보다 빠를 수 없습니다."
			);
		}

		return Duration.between(startedAt, completedAt);
	}

	public void complete() {
		validateInProgress();
		markCompleted();
	}

	private void markCompleted() {
		status = LearningSessionStatus.COMPLETED;
		completedAt = LocalDateTime.now(ZoneOffset.UTC);
	}

	private void validateInProgress() {
		if (status != LearningSessionStatus.IN_PROGRESS) {
			throw new IllegalStateException("진행 중인 학습 세션이 아닙니다.");
		}
	}

	private void validateAnswerSubmissionTarget(Long sessionQuestionId) {
		if (!isInProgress()) {
			throw new AnswerSubmissionReservationException(
				AnswerSubmissionReservationException.Reason.SESSION_NOT_IN_PROGRESS,
				"진행 중인 학습 세션이 아닙니다."
			);
		}

		if (!Objects.equals(questions.getCurrent().getId(), sessionQuestionId)) {
			throw new AnswerSubmissionReservationException(
				AnswerSubmissionReservationException.Reason.CURRENT_QUESTION_MISMATCH,
				"현재 문제가 아닙니다."
			);
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
