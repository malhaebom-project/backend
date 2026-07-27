package com.malhaebom.malhaebom.domain.learning;

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
@Table(name = "questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

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

	@Column(length = 500)
	private String hintText;

	private String ttsUrl;

	public static Question create(
		LearningTopic topic,
		Difficulty difficulty,
		QuestionType type,
		String questionText,
		String questionTextKo,
		String imageUrl,
		String hintText,
		String ttsUrl
	) {
		Question question = new Question();
		question.topic = topic;
		question.difficulty = difficulty;
		question.type = type;
		question.questionText = questionText;
		question.questionTextKo = questionTextKo;
		question.imageUrl = imageUrl;
		question.hintText = hintText;
		question.ttsUrl = ttsUrl;
		return question;
	}
}
