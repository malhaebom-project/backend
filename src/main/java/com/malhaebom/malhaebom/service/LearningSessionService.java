package com.malhaebom.malhaebom.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.Difficulty;
import com.malhaebom.malhaebom.domain.LearningSession;
import com.malhaebom.malhaebom.domain.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.LearningTopic;
import com.malhaebom.malhaebom.domain.Question;
import com.malhaebom.malhaebom.domain.QuestionType;
import com.malhaebom.malhaebom.domain.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.repository.QuestionRepository;
import com.malhaebom.malhaebom.global.exception.LearningSessionNotFoundException;

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
		LearningTopic topic = LearningTopic.fromTopicId(topicId);
		List<Question> candidates =
			questionRepository.findAllByTopicAndDifficultyAndTypeInOrderByIdAsc(
				topic,
				difficulty,
				questionTypes
			);

		if (candidates.size() < questionCount) {
			throw new IllegalArgumentException("요청한 개수만큼 문제를 구성할 수 없습니다.");
		}

		List<Question> selectedQuestions = candidates.subList(0, questionCount);
		LearningSession session =
			LearningSession.create(childId, topic, difficulty, selectedQuestions);

		return learningSessionRepository.save(session);
	}

	public LearningSessionQuestion getNextQuestion(Long sessionId) {
		LearningSession session = getSession(sessionId);
		return session.getCurrentQuestion();
	}

	public LearningSession get(Long sessionId) {
		return getSession(sessionId);
	}

	@Transactional
	public LearningSession complete(Long sessionId) {
		LearningSession session = getSession(sessionId);
		session.complete();
		return session;
	}

	private LearningSession getSession(Long sessionId) {
		return learningSessionRepository.findWithQuestionsById(sessionId)
			.orElseThrow(LearningSessionNotFoundException::new);
	}
}
