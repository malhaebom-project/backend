package com.malhaebom.malhaebom.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.service.dto.AnswerFeedback;
import com.malhaebom.malhaebom.service.port.AnswerFeedbackGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnswerFeedbackService {

	private static final Logger log = LoggerFactory.getLogger(
		AnswerFeedbackService.class
	);

	private final AnswerFeedbackGenerator answerFeedbackGenerator;

	public AnswerFeedback generate(Answer answer) {
		try {
			AnswerFeedback generated = answerFeedbackGenerator.generate(
				answer.getSessionQuestion().getQuestion(),
				answer.getAnswerText(),
				answer.getResult()
			);
			return normalize(generated, answer);
		} catch (RuntimeException exception) {
			log.warn(
				"AI 피드백 생성에 실패해 기본 피드백을 반환합니다. errorType={}",
				exception.getClass().getSimpleName()
			);
			return AnswerFeedback.fallback(answer.getResult());
		}
	}

	private AnswerFeedback normalize(
		AnswerFeedback generated,
		Answer answer
	) {
		if (generated == null || !generated.hasFeedbackText()) {
			return AnswerFeedback.fallback(answer.getResult());
		}

		return new AnswerFeedback(
			generated.matchedKeywords(),
			answer.isCorrect()
				? List.of()
				: generated.missingKeywords(),
			generated.feedbackText()
		);
	}
}
