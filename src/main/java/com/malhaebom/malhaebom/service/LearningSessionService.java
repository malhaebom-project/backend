package com.malhaebom.malhaebom.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearningSessionService {

	private final LearningSessionRepository learningSessionRepository;
	private final QuestionRepository questionRepository;

	@Transactional
	public LearningSession create(
		Long childId,
		Long topicId,
		Difficulty difficulty,
		List<QuestionType> questionTypes,
		int questionCount
	) {
		LearningTopic topic = getTopic(topicId);
		List<Question> candidates =
			questionRepository
				.findAllByTopicAndDifficultyAndTypeInAndActiveTrueAndTtsUrlIsNotNullOrderByIdAsc(
				topic,
				difficulty,
				questionTypes
			);

		if (candidates.size() < questionCount) {
			throw new ApiException(ErrorCode.INSUFFICIENT_QUESTIONS);
		}

		List<Question> selectedQuestions = candidates.subList(0, questionCount);
		LearningSession session =
			LearningSession.create(childId, topic, difficulty, selectedQuestions);

		return learningSessionRepository.save(session);
	}

	public LearningSessionQuestion getNextQuestion(Long sessionId) {
		LearningSession session = getSession(sessionId);
		validateInProgress(session);
		return session.getCurrentQuestion();
	}

	public LearningSession get(Long sessionId) {
		return getSession(sessionId);
	}

	@Transactional
	public LearningSession complete(Long sessionId) {
		LearningSession session = getSession(sessionId);
		if (!session.isCompleted()) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"모든 문제를 완료한 학습 세션이 아닙니다."
			);
		}
		return session;
	}

	private LearningSession getSession(Long sessionId) {
		return learningSessionRepository.findWithQuestionsById(sessionId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_FOUND
			));
	}

	private LearningTopic getTopic(Long topicId) {
		try {
			return LearningTopic.fromTopicId(topicId);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(
				ErrorCode.LEARNING_TOPIC_NOT_FOUND,
				exception
			);
		}
	}

	private void validateInProgress(LearningSession session) {
		if (!session.isInProgress()) {
			throw new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS
			);
		}
	}
}
