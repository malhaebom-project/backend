package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;

public record NextQuestionResponse(
	Long sessionQuestionId,
	Long questionId,
	int questionIndex,
	int totalQuestionCount,
	QuestionType type,
	String questionText,
	String questionTextKo,
	String imageUrl,
	String hintText,
	String ttsUrl
) {

	public static NextQuestionResponse from(
		LearningSessionQuestion sessionQuestion,
		String imageUrl
	) {
		LearningSession session = sessionQuestion.getLearningSession();
		Question question = sessionQuestion.getQuestion();

		return new NextQuestionResponse(
			sessionQuestion.getId(),
			question.getId(),
			sessionQuestion.getQuestionIndex() + 1,
			session.getQuestionCount(),
			question.getType(),
			question.getQuestionText(),
			question.getQuestionTextKo(),
			imageUrl,
			question.getHintText(),
			question.getTtsUrl()
		);
	}
}
