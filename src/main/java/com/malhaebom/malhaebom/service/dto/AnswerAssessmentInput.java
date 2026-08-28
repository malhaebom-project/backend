package com.malhaebom.malhaebom.service.dto;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.malhaebom.malhaebom.domain.learning.AnswerSubmission;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;

public record AnswerAssessmentInput(
	Difficulty difficulty,
	QuestionType questionType,
	String questionText,
	String questionTextKo,
	String gradingContext,
	String modelAnswer,
	Set<String> acceptedAnswers,
	String answerText
) {
	public AnswerAssessmentInput {
		if (difficulty == null) {
			throw new IllegalArgumentException("난이도는 null일 수 없습니다.");
		}
		if (questionType == null) {
			throw new IllegalArgumentException("문제 유형은 null일 수 없습니다.");
		}
		validateText(questionText, "영문 문제");
		validateText(questionTextKo, "한글 문제");
		if (gradingContext == null) {
			throw new IllegalArgumentException("제약 참고 사항은 null일 수 없습니다.");
		}
		validateText(modelAnswer, "모범 답안");
		validateText(answerText, "학습자 답변");
		if (acceptedAnswers == null) {
			throw new IllegalArgumentException("허용 답안 목록은 null일 수 없습니다.");
		}
		if (acceptedAnswers.stream().anyMatch(
			answer -> answer == null || answer.isBlank()
		)) {
			throw new IllegalArgumentException("허용 답안은 비어 있을 수 없습니다.");
		}
		acceptedAnswers = Collections.unmodifiableSet(
			new LinkedHashSet<>(acceptedAnswers)
		);
	}

	public static AnswerAssessmentInput from(AnswerSubmission submission) {
		if (submission == null) {
			throw new IllegalArgumentException("답변 제출 예약은 null일 수 없습니다.");
		}
		Question question = submission.getSessionQuestion().getQuestion();
		SpeechAnswer speechAnswer = submission.getSpeechAnswer();
		return new AnswerAssessmentInput(
			question.getDifficulty(),
			question.getType(),
			question.getQuestionText(),
			question.getQuestionTextKo(),
			question.getGradingContext(),
			question.getModelAnswer(),
			question.getAcceptedAnswers(),
			speechAnswer.getTranscript()
		);
	}

	private static void validateText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + "은/는 비어 있을 수 없습니다.");
		}
	}
}
