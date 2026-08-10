package com.malhaebom.malhaebom.integration.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.AnswerEvaluation;
import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.global.exception.CurrentQuestionMismatchException;
import com.malhaebom.malhaebom.global.exception.SpeechAnswerNotFoundException;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.service.AnswerAssessmentService;
import com.malhaebom.malhaebom.service.AnswerEvaluator;
import com.malhaebom.malhaebom.service.LearningAnswerService;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;
import com.malhaebom.malhaebom.service.port.AnswerAssessmentGenerator;

@DataJpaTest
@Import(JpaAuditingConfiguration.class)
class LearningAnswerServiceJpaTest {

	private static final String ANSWER_TEXT = "He is running.";

	@Autowired
	private LearningSessionRepository learningSessionRepository;
	@Autowired
	private QuestionRepository questionRepository;
	@Autowired
	private SpeechAnswerRepository speechAnswerRepository;
	@Autowired
	private AnswerRepository answerRepository;

	private TestAnswerAssessmentGenerator assessmentGenerator;
	private LearningAnswerService learningAnswerService;

	@BeforeEach
	void setUp() {
		assessmentGenerator = new TestAnswerAssessmentGenerator();
		AnswerAssessmentService assessmentService =
			new AnswerAssessmentService(
				assessmentGenerator,
				new AnswerEvaluator()
			);
		learningAnswerService = new LearningAnswerService(
			learningSessionRepository,
			answerRepository,
			speechAnswerRepository,
			assessmentService
		);
	}

	@Test
	void 완료된_음성_답변을_채점하고_답변과_세션_결과를_저장한다() {
		LearningSession session = saveSession();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		assessmentGenerator.willReturn(correctAssessment());

		AnswerSubmissionResult result = learningAnswerService.submit(
			session.getId(),
			session.getCurrentQuestion().getId(),
			speechAnswer.getId()
		);

		assertEquals(1, answerRepository.count());
		assertEquals(ANSWER_TEXT, result.answer().getAnswerText());
		assertEquals(1, result.answer().getAttemptNo());
		assertTrue(result.answer().isCorrect());
		assertTrue(session.isCompleted());
		assertFalse(result.canRetry());
		assertEquals(0, result.remainingAttempts());
	}

	@Test
	void 부분_정답의_동적_점수와_재시도_상태를_저장한다() {
		LearningSession session = saveSession();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		assessmentGenerator.willReturn(new AnswerAssessment(
			true,
			40,
			23,
			15,
			"동작 표현은 좋았어요. 주어를 함께 말해 보세요."
		));

		AnswerSubmissionResult result = learningAnswerService.submit(
			session.getId(),
			session.getCurrentQuestion().getId(),
			speechAnswer.getId()
		);

		assertEquals(AnswerResult.PARTIALLY_CORRECT, result.answer().getResult());
		assertEquals(78, result.answer().getScore());
		assertTrue(result.canRetry());
		assertEquals(1, result.remainingAttempts());
		assertEquals(1, session.getCurrentQuestion().getWrongAnswerCount());
	}

	@Test
	void 존재하지_않는_음성_답변은_채점하지_않고_거부한다() {
		LearningSession session = saveSession();

		assertThrows(
			SpeechAnswerNotFoundException.class,
			() -> learningAnswerService.submit(
				session.getId(),
				session.getCurrentQuestion().getId(),
				999L
			)
		);
		assertEquals(0, assessmentGenerator.callCount);
		assertEquals(0, answerRepository.count());
	}

	@Test
	void 다른_문제의_음성_답변은_채점하지_않고_거부한다() {
		LearningSession currentSession = saveSession();
		SpeechAnswer otherSpeechAnswer = saveCompletedSpeechAnswer(
			saveSession()
		);

		assertThrows(
			CurrentQuestionMismatchException.class,
			() -> learningAnswerService.submit(
				currentSession.getId(),
				currentSession.getCurrentQuestion().getId(),
				otherSpeechAnswer.getId()
			)
		);
		assertEquals(0, assessmentGenerator.callCount);
		assertEquals(0, answerRepository.count());
	}

	@Test
	void 처리_중인_음성_답변은_채점하지_않고_거부한다() {
		LearningSession session = saveSession();
		SpeechAnswer processing = speechAnswerRepository.saveAndFlush(
			SpeechAnswer.start(
				session.getCurrentQuestion(),
				"processing-request-key",
				1
			)
		);

		assertThrows(
			IllegalStateException.class,
			() -> learningAnswerService.submit(
				session.getId(),
				session.getCurrentQuestion().getId(),
				processing.getId()
			)
		);
		assertEquals(0, assessmentGenerator.callCount);
		assertEquals(0, answerRepository.count());
	}

	@Test
	void 이미_제출에_사용한_음성_답변은_다시_채점하지_않는다() {
		LearningSession session = saveSession();
		LearningSessionQuestion sessionQuestion = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = saveCompletedSpeechAnswer(session);
		answerRepository.saveAndFlush(Answer.create(
			sessionQuestion,
			speechAnswer,
			1,
			AnswerEvaluation.from(AnswerResult.INCORRECT)
		));

		assertThrows(
			IllegalStateException.class,
			() -> learningAnswerService.submit(
				session.getId(),
				sessionQuestion.getId(),
				speechAnswer.getId()
			)
		);
		assertEquals(0, assessmentGenerator.callCount);
		assertEquals(1, answerRepository.count());
	}

	private LearningSession saveSession() {
		return LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			"He is ____ing."
		);
	}

	private SpeechAnswer saveCompletedSpeechAnswer(LearningSession session) {
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			session.getCurrentQuestion(),
			"request-key-" + session.getId(),
			1
		);
		speechAnswer.complete(ANSWER_TEXT, 0.94, "TEST_STT");
		return speechAnswerRepository.saveAndFlush(speechAnswer);
	}

	private AnswerAssessment correctAssessment() {
		return new AnswerAssessment(
			true,
			50,
			30,
			20,
			"현재진행형을 정확하게 사용했어요!"
		);
	}

	private static final class TestAnswerAssessmentGenerator
		implements AnswerAssessmentGenerator {

		private AnswerAssessment assessment;
		private int callCount;

		void willReturn(AnswerAssessment assessment) {
			this.assessment = assessment;
		}

		@Override
		public AnswerAssessment generate(
			Question question,
			String answerText
		) {
			callCount++;
			return assessment;
		}
	}
}
