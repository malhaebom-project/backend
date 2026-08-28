package com.malhaebom.malhaebom.integration.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.persistence.EntityManager;

import com.malhaebom.malhaebom.domain.learning.AnswerSubmission;
import com.malhaebom.malhaebom.domain.learning.AnswerSubmissionStatus;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerSubmissionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;

@DataJpaTest
@Import(JpaAuditingConfiguration.class)
class AnswerSubmissionRepositoryJpaTest {
	@Autowired
	private AnswerSubmissionRepository answerSubmissionRepository;
	@Autowired
	private LearningSessionRepository learningSessionRepository;
	@Autowired
	private QuestionRepository questionRepository;
	@Autowired
	private SpeechAnswerRepository speechAnswerRepository;
	@Autowired
	private EntityManager entityManager;

	@Test
	void 제출_예약을_저장한다() {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(question, 1);
		AnswerSubmission saved = answerSubmissionRepository.saveAndFlush(
			session.answerSubmissionTarget(question.getId())
				.reserve(speechAnswer, 1)
		);

		entityManager.clear();
		AnswerSubmission found = answerSubmissionRepository.findById(
			saved.getId()
		).orElseThrow();

		assertEquals(AnswerSubmissionStatus.PENDING, found.getStatus());
		assertEquals(question.getId(), found.getSessionQuestion().getId());
		assertEquals(speechAnswer.getId(), found.getSpeechAnswer().getId());
		assertEquals(1, found.getAttemptNo());
		assertEquals(
			saved.getId(),
			answerSubmissionRepository.findBySpeechAnswer_Id(
				speechAnswer.getId()
			).orElseThrow().getId()
		);
	}

	@Test
	void 같은_음성_답변은_두_번_예약할_수_없다() {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(question, 1);
		answerSubmissionRepository.saveAndFlush(
			session.answerSubmissionTarget(question.getId())
				.reserve(speechAnswer, 1)
		);

		assertThrows(
			DataIntegrityViolationException.class,
			() -> answerSubmissionRepository.saveAndFlush(
				session.answerSubmissionTarget(question.getId())
					.reserve(speechAnswer, 2)
			)
		);
	}

	@Test
	void 같은_문제의_같은_시도_번호는_두_번_예약할_수_없다() {
		LearningSession session = saveSession();
		LearningSessionQuestion question = session.getCurrentQuestion();
		SpeechAnswer first = saveCompletedSpeechAnswer(question, 1);
		SpeechAnswer second = saveCompletedSpeechAnswer(question, 2);
		answerSubmissionRepository.saveAndFlush(
			session.answerSubmissionTarget(question.getId()).reserve(first, 1)
		);

		assertThrows(
			DataIntegrityViolationException.class,
			() -> answerSubmissionRepository.saveAndFlush(
				session.answerSubmissionTarget(question.getId()).reserve(second, 1)
			)
		);
	}

	private LearningSession saveSession() {
		return LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			"He is ____ing."
		);
	}

	private SpeechAnswer saveCompletedSpeechAnswer(LearningSessionQuestion question, int recordingNo) {
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			question,
			"request-key-" + recordingNo,
			recordingNo
		);
		speechAnswer.complete("He is running.", 0.94, "TEST_STT");
		return speechAnswerRepository.saveAndFlush(speechAnswer);
	}
}
