package com.malhaebom.malhaebom.domain.learning;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningSessionQuestions {

	@OneToMany(
		mappedBy = "learningSession",
		cascade = CascadeType.ALL,
		orphanRemoval = true
	)
	@OrderBy("questionIndex ASC")
	private List<LearningSessionQuestion> values = new ArrayList<>();

	@Column(name = "current_question_index", nullable = false)
	private int currentQuestionIndex;

	void addAll(LearningSession learningSession, List<Question> questions) {
		validateQuestions(questions);

		for (int index = 0; index < questions.size(); index++) {
			values.add(
				LearningSessionQuestion.create(
					learningSession,
					questions.get(index),
					index
				)
			);
		}
	}

	LearningSessionQuestion getCurrent() {
		if (isCompleted()) {
			throw new IllegalStateException("진행할 문제가 없습니다.");
		}

		return values.get(currentQuestionIndex);
	}

	void completeCurrent(boolean correct) {
		LearningSessionQuestion currentQuestion = getCurrent();
		currentQuestion.complete(correct);
		currentQuestionIndex++;
	}

	void useHintOnCurrentQuestion() {
		getCurrent().useHint();
	}

	int getCurrentQuestionIndex() {
		return currentQuestionIndex;
	}

	int size() {
		return values.size();
	}

	int getCorrectCount() {
		return (int) values.stream()
			.filter(LearningSessionQuestion::isCorrect)
			.count();
	}

	boolean isCompleted() {
		return !values.isEmpty() && currentQuestionIndex >= values.size();
	}

	private void validateQuestions(List<Question> questions) {
		if (questions == null || questions.isEmpty()) {
			throw new IllegalArgumentException("학습 세션에는 문제가 한 개 이상 필요합니다.");
		}

		if (questions.stream().anyMatch(question -> question == null)) {
			throw new IllegalArgumentException("문제는 null일 수 없습니다.");
		}

		if (questions.stream().distinct().count() != questions.size()) {
			throw new IllegalArgumentException("동일한 문제를 중복 배정할 수 없습니다.");
		}
	}
}
