package com.malhaebom.malhaebom.domain.learning;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.malhaebom.malhaebom.domain.BaseEntity;

@Entity
@Table(
	name = "answer_submissions",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_answer_submissions_speech_answer",
			columnNames = "speech_answer_id"
		),
		@UniqueConstraint(
			name = "uk_answer_submissions_attempt",
			columnNames = {"session_question_id", "attempt_no"}
		)
	},
	indexes = {
		@Index(
			name = "idx_answer_submissions_status_lease",
			columnList = "status, lease_expires_at"
		)
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnswerSubmission extends BaseEntity {

	private static final int MAX_PROCESSING_TOKEN_LENGTH = 36;
	private static final int MAX_FAILURE_MESSAGE_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_question_id", nullable = false)
	private LearningSessionQuestion sessionQuestion;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "speech_answer_id", nullable = false)
	private SpeechAnswer speechAnswer;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "answer_id", unique = true)
	private Answer answer;

	@Column(name = "attempt_no", nullable = false)
	private int attemptNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AnswerSubmissionStatus status;

	@Column(name = "processing_token", length = 36)
	private String processingToken;

	@Column(name = "lease_expires_at")
	private Instant leaseExpiresAt;

	@Column(name = "failure_message", length = 1000)
	private String failureMessage;

	@Column(name = "question_text_snapshot", nullable = false, length = 500)
	private String questionTextSnapshot;

	@Column(name = "question_text_ko_snapshot", nullable = false, length = 500)
	private String questionTextKoSnapshot;

	@Column(name = "model_answer_snapshot", nullable = false, length = 1000)
	private String modelAnswerSnapshot;

	@ElementCollection
	@CollectionTable(
		name = "answer_submission_accepted_answers",
		joinColumns = @JoinColumn(name = "answer_submission_id")
	)
	@Column(name = "answer_text", nullable = false, length = 1000)
	@Getter(AccessLevel.NONE)
	private Set<String> acceptedAnswersSnapshot = new LinkedHashSet<>();

	@Column(name = "answer_text_snapshot", nullable = false, length = 4000)
	private String answerTextSnapshot;

	public static AnswerSubmission reserve(
		LearningSessionQuestion sessionQuestion,
		SpeechAnswer speechAnswer,
		int attemptNo
	) {
		validateReservation(sessionQuestion, speechAnswer, attemptNo);

		Question question = sessionQuestion.getQuestion();
		AnswerSubmission submission = new AnswerSubmission();
		submission.sessionQuestion = sessionQuestion;
		submission.speechAnswer = speechAnswer;
		submission.attemptNo = attemptNo;
		submission.status = AnswerSubmissionStatus.PENDING;
		submission.questionTextSnapshot = question.getQuestionText();
		submission.questionTextKoSnapshot = question.getQuestionTextKo();
		submission.modelAnswerSnapshot = question.getModelAnswer();
		submission.acceptedAnswersSnapshot.addAll(
			question.getAcceptedAnswers()
		);
		submission.answerTextSnapshot = speechAnswer.getTranscript();
		return submission;
	}

	public Set<String> getAcceptedAnswersSnapshot() {
		return Collections.unmodifiableSet(acceptedAnswersSnapshot);
	}

	public void claim(
		String processingToken,
		Instant claimedAt,
		Instant leaseExpiresAt
	) {
		validateClaim(processingToken, claimedAt, leaseExpiresAt);

		status = AnswerSubmissionStatus.PROCESSING;
		this.processingToken = processingToken;
		this.leaseExpiresAt = leaseExpiresAt;
		failureMessage = null;
	}

	public void complete(String processingToken, Answer answer) {
		validateProcessingToken(processingToken);
		validateCompletedAnswer(answer);

		status = AnswerSubmissionStatus.COMPLETED;
		this.answer = answer;
		this.processingToken = null;
		leaseExpiresAt = null;
		failureMessage = null;
	}

	public void fail(String processingToken, String failureMessage) {
		validateProcessingToken(processingToken);
		validateText(
			failureMessage,
			MAX_FAILURE_MESSAGE_LENGTH,
			"실패 사유"
		);

		status = AnswerSubmissionStatus.FAILED;
		this.processingToken = null;
		leaseExpiresAt = null;
		this.failureMessage = failureMessage;
	}

	public boolean isLeaseExpiredAt(Instant instant) {
		Objects.requireNonNull(instant, "기준 시각은 null일 수 없습니다.");
		return status == AnswerSubmissionStatus.PROCESSING
			&& leaseExpiresAt != null
			&& !leaseExpiresAt.isAfter(instant);
	}

	private void validateClaim(
		String processingToken,
		Instant claimedAt,
		Instant leaseExpiresAt
	) {
		validateText(
			processingToken,
			MAX_PROCESSING_TOKEN_LENGTH,
			"처리 토큰"
		);
		Objects.requireNonNull(claimedAt, "선점 시각은 null일 수 없습니다.");
		Objects.requireNonNull(
			leaseExpiresAt,
			"처리 임대 만료 시각은 null일 수 없습니다."
		);

		boolean pending = status == AnswerSubmissionStatus.PENDING;
		boolean expiredProcessing = isLeaseExpiredAt(claimedAt);
		if (!pending && !expiredProcessing) {
			throw new IllegalStateException("선점 가능한 답변 제출 예약이 아닙니다.");
		}

		if (!leaseExpiresAt.isAfter(claimedAt)) {
			throw new IllegalArgumentException(
				"처리 임대 만료 시각은 선점 시각보다 이후여야 합니다."
			);
		}
	}

	private void validateProcessingToken(String processingToken) {
		validateText(
			processingToken,
			MAX_PROCESSING_TOKEN_LENGTH,
			"처리 토큰"
		);

		if (status != AnswerSubmissionStatus.PROCESSING) {
			throw new IllegalStateException("처리 중인 답변 제출 예약이 아닙니다.");
		}

		if (!Objects.equals(this.processingToken, processingToken)) {
			throw new IllegalStateException("답변 제출 예약의 처리 토큰이 일치하지 않습니다.");
		}
	}

	private void validateCompletedAnswer(Answer answer) {
		if (answer == null) {
			throw new IllegalArgumentException("완료 답변은 null일 수 없습니다.");
		}

		if (!isSameQuestion(answer.getSessionQuestion(), sessionQuestion)
			|| !isSameSpeechAnswer(answer.getSpeechAnswer(), speechAnswer)
			|| answer.getAttemptNo() != attemptNo) {
			throw new IllegalArgumentException(
				"예약된 제출과 일치하는 답변만 완료 처리할 수 있습니다."
			);
		}
	}

	private static void validateReservation(
		LearningSessionQuestion sessionQuestion,
		SpeechAnswer speechAnswer,
		int attemptNo
	) {
		if (sessionQuestion == null) {
			throw new IllegalArgumentException("세션 문제는 null일 수 없습니다.");
		}

		if (!sessionQuestion.getLearningSession().isInProgress()
			|| sessionQuestion.isCompleted()) {
			throw new IllegalStateException("진행 중인 문제만 제출을 예약할 수 있습니다.");
		}

		LearningSessionQuestion currentQuestion = sessionQuestion
			.getLearningSession()
			.getCurrentQuestion();
		if (!isSameQuestion(sessionQuestion, currentQuestion)) {
			throw new IllegalStateException("현재 문제만 제출을 예약할 수 있습니다.");
		}

		if (speechAnswer == null) {
			throw new IllegalArgumentException("음성 답변은 null일 수 없습니다.");
		}

		if (!speechAnswer.isUsableFor(sessionQuestion)) {
			throw new IllegalArgumentException(
				"현재 문제에 사용할 수 있는 완료된 음성 답변이 아닙니다."
			);
		}

		if (attemptNo < 1) {
			throw new IllegalArgumentException("답변 시도 번호는 1 이상이어야 합니다.");
		}
	}

	private static boolean isSameQuestion(
		LearningSessionQuestion first,
		LearningSessionQuestion second
	) {
		if (first == second) {
			return true;
		}

		return first.getId() != null
			&& second.getId() != null
			&& Objects.equals(first.getId(), second.getId());
	}

	private static boolean isSameSpeechAnswer(
		SpeechAnswer first,
		SpeechAnswer second
	) {
		if (first == second) {
			return true;
		}

		return first != null
			&& second != null
			&& first.getId() != null
			&& Objects.equals(first.getId(), second.getId());
	}

	private static void validateText(
		String value,
		int maximumLength,
		String fieldName
	) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + "은/는 비어 있을 수 없습니다.");
		}

		if (value.length() > maximumLength) {
			throw new IllegalArgumentException(
				fieldName + "은/는 " + maximumLength + "자를 초과할 수 없습니다."
			);
		}
	}
}
