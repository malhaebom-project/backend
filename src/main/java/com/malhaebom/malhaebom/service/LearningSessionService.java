package com.malhaebom.malhaebom.service;

import java.util.ArrayList;
import java.util.Collections;
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
	private final ChildProfileService childProfileService;

	@Transactional
	public LearningSession create(
		Long userId,
		Long childId,
		Long topicId,
		Difficulty difficulty,
		List<QuestionType> questionTypes,
		int questionCount
	) {
		childProfileService.getOwnedActive(userId, childId);
		LearningTopic topic = getTopic(topicId);
		List<Question> candidates =
			questionRepository.findAllByTopicAndDifficultyAndTypeInAndActiveTrue(
				topic,
				difficulty,
				questionTypes
			);

		if (candidates.size() < questionCount) {
			throw new ApiException(ErrorCode.INSUFFICIENT_QUESTIONS);
		}

		List<Question> shuffledCandidates = new ArrayList<>(candidates);
		Collections.shuffle(shuffledCandidates);
		List<Question> selectedQuestions = List.copyOf(shuffledCandidates.subList(0, questionCount));
		LearningSession session =
			LearningSession.create(childId, topic, difficulty, selectedQuestions);

		return learningSessionRepository.save(session);
	}

	@Transactional(readOnly = true)
	public LearningSessionQuestion getNextQuestion(Long userId, Long sessionId) {
		LearningSession session = getSession(sessionId);
		validateOwned(userId, session);
		validateInProgress(session);
		return session.getCurrentQuestion();
	}

	@Transactional(readOnly = true)
	public LearningSession get(Long userId, Long sessionId) {
		LearningSession session = getSession(sessionId);
		validateOwned(userId, session);
		return session;
	}

	@Transactional
	public LearningSession complete(Long userId, Long sessionId) {
		LearningSession session = getSession(sessionId);
		validateOwned(userId, session);
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

	private void validateOwned(Long userId, LearningSession session) {
		childProfileService.getOwnedActive(userId, session.getChildId());
	}

	private void validateInProgress(LearningSession session) {
		if (!session.isInProgress()) {
			throw new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS
			);
		}
	}
}
