package com.malhaebom.malhaebom.domain.learning;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.malhaebom.malhaebom.domain.BaseEntity;

@Entity
@Table(name = "questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private LearningTopic topic;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Difficulty difficulty;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private QuestionType type;

	@Column(nullable = false, length = 500)
	private String questionText;

	@Column(nullable = false, length = 500)
	private String questionTextKo;

	private String imageUrl;

	@Column(nullable = false, length = 1000)
	private String modelAnswer;

	@ElementCollection
	@CollectionTable(
		name = "question_accepted_answers",
		joinColumns = @JoinColumn(name = "question_id")
	)
	@Column(name = "answer_text", nullable = false, length = 1000)
	@Getter(AccessLevel.NONE)
	private Set<String> acceptedAnswers = new LinkedHashSet<>();

	@Column(length = 500)
	private String hintText;

	private String ttsUrl;

	@Column(nullable = false)
	private boolean active;

	public static Question create(
		LearningTopic topic,
		Difficulty difficulty,
		QuestionType type,
		String questionText,
		String questionTextKo,
		String imageUrl,
		String modelAnswer,
		Set<String> acceptedAnswers,
		String hintText,
		String ttsUrl
	) {
		validateQuestionValues(
			topic,
			difficulty,
			type,
			questionText,
			questionTextKo
		);
		validateAnswers(modelAnswer, acceptedAnswers);

		Question question = new Question();
		question.topic = topic;
		question.difficulty = difficulty;
		question.type = type;
		question.questionText = questionText;
		question.questionTextKo = questionTextKo;
		question.imageUrl = imageUrl;
		question.modelAnswer = modelAnswer;
		question.acceptedAnswers.addAll(acceptedAnswers);
		question.hintText = hintText;
		question.ttsUrl = ttsUrl;
		question.active = true;
		return question;
	}

	public boolean update(
		LearningTopic topic,
		Difficulty difficulty,
		QuestionType type,
		String questionText,
		String questionTextKo,
		String imageUrl,
		String modelAnswer,
		Set<String> acceptedAnswers,
		String hintText
	) {
		validateQuestionValues(
			topic,
			difficulty,
			type,
			questionText,
			questionTextKo
		);
		validateAnswers(modelAnswer, acceptedAnswers);

		boolean ttsRegenerationRequired =
			!Objects.equals(this.questionText, questionText);

		this.topic = topic;
		this.difficulty = difficulty;
		this.type = type;
		this.questionText = questionText;
		this.questionTextKo = questionTextKo;
		this.imageUrl = imageUrl;
		this.modelAnswer = modelAnswer;
		this.acceptedAnswers.clear();
		this.acceptedAnswers.addAll(acceptedAnswers);
		this.hintText = hintText;

		if (ttsRegenerationRequired) {
			this.ttsUrl = null;
		}

		return ttsRegenerationRequired;
	}

	public void updateTtsUrl(String ttsUrl) {
		this.ttsUrl = ttsUrl;
	}

	public void deactivate() {
		active = false;
	}

	public Set<String> getAcceptedAnswers() {
		return Collections.unmodifiableSet(acceptedAnswers);
	}

	public boolean matchesAnswer(String answerText) {
		if (answerText == null || answerText.isBlank()) {
			return false;
		}

		String normalizedAnswer = normalize(answerText);
		if (normalize(modelAnswer).equals(normalizedAnswer)) {
			return true;
		}

		return acceptedAnswers.stream()
			.map(Question::normalize)
			.anyMatch(normalizedAnswer::equals);
	}

	private static void validateQuestionValues(
		LearningTopic topic,
		Difficulty difficulty,
		QuestionType type,
		String questionText,
		String questionTextKo
	) {
		if (topic == null || difficulty == null || type == null) {
			throw new IllegalArgumentException(
				"주제, 난이도, 문제 유형은 필수입니다."
			);
		}

		if (questionText == null || questionText.isBlank()) {
			throw new IllegalArgumentException("영문 문제는 비어 있을 수 없습니다.");
		}

		if (questionTextKo == null || questionTextKo.isBlank()) {
			throw new IllegalArgumentException("한글 문제는 비어 있을 수 없습니다.");
		}
	}

	private static void validateAnswers(
		String modelAnswer,
		Set<String> acceptedAnswers
	) {
		if (modelAnswer == null || modelAnswer.isBlank()) {
			throw new IllegalArgumentException("모범 답안은 비어 있을 수 없습니다.");
		}

		if (acceptedAnswers == null) {
			throw new IllegalArgumentException("허용 답안 목록은 null일 수 없습니다.");
		}

		if (acceptedAnswers.stream().anyMatch(
			answer -> answer == null || answer.isBlank()
		)) {
			throw new IllegalArgumentException("허용 답안은 비어 있을 수 없습니다.");
		}
	}

	private static String normalize(String answer) {
		return answer.strip() // 앞 뒤 공백 제거
			.toLowerCase(Locale.ENGLISH) // 소문자로 변환
			.replaceAll("\\s+", " ") // 연속 공백 하나로 합치기
			.replaceAll("[.!?]+$", ""); // 문장 끝의 ., ?, ! 제거
	}
}
