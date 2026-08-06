package com.malhaebom.malhaebom.domain.learning;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
	name = "answers",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_answers_attempt",
			columnNames = {"session_question_id", "attempt_no"}
		)
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Answer extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_question_id", nullable = false)
	private LearningSessionQuestion sessionQuestion;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "speech_answer_id", unique = true)
	private SpeechAnswer speechAnswer;

	@Column(name = "attempt_no", nullable = false)
	private int attemptNo;

	@Column(nullable = false, length = 4000)
	private String answerText;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AnswerResult result;

	@Column(nullable = false)
	private int score;

	@Column(nullable = false, length = 1000)
	private String modelAnswerSnapshot;

	@Column(nullable = false)
	private LocalDateTime submittedAt;

	public static Answer create(
		LearningSessionQuestion sessionQuestion,
		SpeechAnswer speechAnswer,
		int attemptNo,
		AnswerEvaluation evaluation
	) {
		validateCreation(
			sessionQuestion,
			speechAnswer,
			attemptNo,
			evaluation
		);

		Answer answer = new Answer();
		answer.sessionQuestion = sessionQuestion;
		answer.speechAnswer = speechAnswer;
		answer.attemptNo = attemptNo;
		answer.answerText = speechAnswer.getTranscript();
		answer.result = evaluation.result();
		answer.score = evaluation.score();
		answer.modelAnswerSnapshot = sessionQuestion
			.getQuestion()
			.getModelAnswer();
		answer.submittedAt = LocalDateTime.now();
		return answer;
	}

	public boolean isCorrect() {
		return result.isCorrect();
	}

	private static void validateCreation(
		LearningSessionQuestion sessionQuestion,
		SpeechAnswer speechAnswer,
		int attemptNo,
		AnswerEvaluation evaluation
	) {
		if (sessionQuestion == null) {
			throw new IllegalArgumentException("세션 문제는 null일 수 없습니다.");
		}

		if (sessionQuestion.isCompleted()) {
			throw new IllegalStateException("이미 완료한 문제에는 답변할 수 없습니다.");
		}

		if (speechAnswer == null) {
			throw new IllegalArgumentException("음성 답변은 null일 수 없습니다.");
		}

		if (!speechAnswer.isUsableFor(sessionQuestion)) {
			throw new IllegalArgumentException(
				"현재 문제에 사용할 수 있는 완료된 음성 답변이 아닙니다."
			);
		}

		if (speechAnswer.getTranscript() == null
			|| speechAnswer.getTranscript().isBlank()) {
			throw new IllegalArgumentException("답변은 비어 있을 수 없습니다.");
		}

		if (attemptNo < 1) {
			throw new IllegalArgumentException("답변 시도 번호는 1 이상이어야 합니다.");
		}

		if (evaluation == null) {
			throw new IllegalArgumentException("채점 결과는 null일 수 없습니다.");
		}
	}
}
