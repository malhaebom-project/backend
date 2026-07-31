package com.malhaebom.malhaebom.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.User;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.domain.repository.UserRepository;
import com.malhaebom.malhaebom.global.exception.ForbiddenException;
import com.malhaebom.malhaebom.global.exception.QuestionNotFoundException;
import com.malhaebom.malhaebom.service.dto.AdminQuestionCommand;
import com.malhaebom.malhaebom.service.event.QuestionTtsRequestedEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminQuestionService {

	private final UserRepository userRepository;
	private final QuestionRepository questionRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public Question create(Long userId, AdminQuestionCommand command) {
		validateAdmin(userId);

		Question question = questionRepository.save(
			Question.create(
				command.topic(),
				command.difficulty(),
				command.type(),
				command.questionText(),
				command.questionTextKo(),
				command.imageUrl(),
				command.modelAnswer(),
				command.acceptedAnswers(),
				command.hintText(),
				null
			)
		);
		eventPublisher.publishEvent(
			new QuestionTtsRequestedEvent(
				question.getId(),
				question.getQuestionText()
			)
		);
		return question;
	}

	public List<Question> getAll(Long userId) {
		validateAdmin(userId);
		return questionRepository.findAllByActiveTrueOrderByIdDesc();
	}

	public Question get(Long userId, Long questionId) {
		validateAdmin(userId);
		return getActiveQuestion(questionId);
	}

	@Transactional
	public Question update(
		Long userId,
		Long questionId,
		AdminQuestionCommand command
	) {
		validateAdmin(userId);
		Question question = getActiveQuestion(questionId);

		boolean ttsRegenerationRequired = question.update(
			command.topic(),
			command.difficulty(),
			command.type(),
			command.questionText(),
			command.questionTextKo(),
			command.imageUrl(),
			command.modelAnswer(),
			command.acceptedAnswers(),
			command.hintText()
		);
		if (ttsRegenerationRequired) {
			eventPublisher.publishEvent(
				new QuestionTtsRequestedEvent(
					question.getId(),
					question.getQuestionText()
				)
			);
		}

		return question;
	}

	@Transactional
	public void delete(Long userId, Long questionId) {
		validateAdmin(userId);
		getActiveQuestion(questionId).deactivate();
	}

	private void validateAdmin(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ForbiddenException(
				"관리자 권한이 필요합니다."
			));

		if (!user.isAdmin()) {
			throw new ForbiddenException("관리자 권한이 필요합니다.");
		}
	}

	private Question getActiveQuestion(Long questionId) {
		return questionRepository.findByIdAndActiveTrue(questionId)
			.orElseThrow(QuestionNotFoundException::new);
	}
}
