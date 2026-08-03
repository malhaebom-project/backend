package com.malhaebom.malhaebom.domain.learning;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.malhaebom.malhaebom.domain.BaseEntity;

@Entity
@Table(
	name = "speech_answers",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_speech_answers_request",
			columnNames = "request_key"
		),
		@UniqueConstraint(
			name = "uk_speech_answers_recording",
			columnNames = {"session_question_id", "recording_no"}
		)
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeechAnswer extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_question_id", nullable = false)
	private LearningSessionQuestion sessionQuestion;

	@Column(name = "request_key", nullable = false, length = 100)
	private String requestKey;

	@Column(name = "recording_no", nullable = false)
	private int recordingNo;

	@Column(length = 4000)
	private String transcript;

	@Column(columnDefinition = "NUMERIC(5, 4)")
	private Double confidence;

	@Column(name = "stt_provider", length = 50)
	private String sttProvider;

	@Enumerated(EnumType.STRING)
	@Column(name = "processing_status", nullable = false, length = 20)
	private SpeechProcessingStatus processingStatus;

	@Column(name = "failure_message", length = 1000)
	private String failureMessage;

	public static SpeechAnswer start(
		LearningSessionQuestion sessionQuestion,
		String requestKey,
		int recordingNo
	) {
		validateStart(sessionQuestion, requestKey, recordingNo);

		SpeechAnswer speechAnswer = new SpeechAnswer();
		speechAnswer.sessionQuestion = sessionQuestion;
		speechAnswer.requestKey = requestKey;
		speechAnswer.recordingNo = recordingNo;
		speechAnswer.processingStatus = SpeechProcessingStatus.PROCESSING;
		return speechAnswer;
	}

	public void complete(
		String transcript,
		Double confidence,
		String sttProvider
	) {
		validateProcessing();
		validateTranscript(transcript);
		validateConfidence(confidence);
		validateSttProvider(sttProvider);

		this.transcript = transcript;
		this.confidence = confidence;
		this.sttProvider = sttProvider;
		this.processingStatus = SpeechProcessingStatus.COMPLETED;
	}

	public void fail(String failureMessage, String sttProvider) {
		validateProcessing();

		this.failureMessage = failureMessage;
		this.sttProvider = sttProvider;
		this.processingStatus = SpeechProcessingStatus.FAILED;
	}

	public boolean isCompleted() {
		return processingStatus == SpeechProcessingStatus.COMPLETED;
	}

	public boolean isUsableFor(LearningSessionQuestion sessionQuestion) {
		if (!isCompleted() || sessionQuestion == null) {
			return false;
		}

		if (this.sessionQuestion == sessionQuestion) {
			return true;
		}

		return this.sessionQuestion.getId() != null
			&& this.sessionQuestion.getId().equals(sessionQuestion.getId());
	}

	private void validateProcessing() {
		if (processingStatus != SpeechProcessingStatus.PROCESSING) {
			throw new IllegalStateException("처리가 종료된 음성 답변의 상태는 변경할 수 없습니다.");
		}
	}

	private static void validateStart(
		LearningSessionQuestion sessionQuestion,
		String requestKey,
		int recordingNo
	) {
		if (sessionQuestion == null) {
			throw new IllegalArgumentException("세션 문제는 null일 수 없습니다.");
		}

		if (sessionQuestion.isCompleted()) {
			throw new IllegalStateException("이미 완료한 문제에는 음성 답변을 생성할 수 없습니다.");
		}

		validateText(requestKey, 100, "요청 키");

		if (recordingNo < 1) {
			throw new IllegalArgumentException("녹음 순번은 1 이상이어야 합니다.");
		}
	}

	private static void validateTranscript(String transcript) {
		validateText(transcript, 4000, "변환된 답변");
	}

	private static void validateConfidence(Double confidence) {
		if (confidence == null) {
			return;
		}

		if (!Double.isFinite(confidence)
			|| confidence < 0.0
			|| confidence > 1.0) {
			throw new IllegalArgumentException("신뢰도는 0 이상 1 이하여야 합니다.");
		}
	}

	private static void validateSttProvider(String sttProvider) {
		validateText(sttProvider, 50, "STT 제공자");
	}

	private static void validateText(
		String value,
		int maximumLength,
		String fieldName
	) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
		}

		if (value.length() > maximumLength) {
			throw new IllegalArgumentException(
				fieldName + "은/는 " + maximumLength + "자를 초과할 수 없습니다."
			);
		}
	}
}
