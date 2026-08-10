package com.malhaebom.malhaebom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.global.exception.CurrentQuestionMismatchException;
import com.malhaebom.malhaebom.global.exception.SpeechAnswerNotFoundException;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;

@ExtendWith(MockitoExtension.class)
class LearningAnswerServiceTest {

	private static final Long SESSION_ID = 10L;
	private static final Long SESSION_QUESTION_ID = 20L;
	private static final Long SPEECH_ANSWER_ID = 30L;
	private static final String ANSWER_TEXT = "He is running.";

	@Mock
	private LearningSessionRepository learningSessionRepository;

	@Mock
	private AnswerRepository answerRepository;

	@Mock
	private SpeechAnswerRepository speechAnswerRepository;

	@Mock
	private AnswerAssessmentService answerAssessmentService;

	private LearningAnswerService learningAnswerService;
	private LearningSession session;
	private LearningSessionQuestion currentQuestion;

	@BeforeEach
	void setUp() {
		learningAnswerService = new LearningAnswerService(
			learningSessionRepository,
			answerRepository,
			speechAnswerRepository,
			answerAssessmentService
		);
		session = createSession();
		currentQuestion = session.getCurrentQuestion();
		ReflectionTestUtils.setField(session, "id", SESSION_ID);
		ReflectionTestUtils.setField(
			currentQuestion,
			"id",
			SESSION_QUESTION_ID
		);
	}

	@Test
	void 현재_문제의_완료된_음성_답변으로_답변을_제출한다() {
		SpeechAnswer speechAnswer = completedSpeechAnswer(currentQuestion);
		AnswerAssessment assessment = correctAssessment();
		prepareSession();
		when(speechAnswerRepository.findById(SPEECH_ANSWER_ID))
			.thenReturn(Optional.of(speechAnswer));
		when(
			answerRepository
				.findFirstBySessionQuestion_IdOrderByAttemptNoDesc(
					SESSION_QUESTION_ID
				)
		).thenReturn(Optional.empty());
		when(answerRepository.save(any(Answer.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
		when(answerAssessmentService.assess(
			currentQuestion.getQuestion(),
			ANSWER_TEXT
		)).thenReturn(assessment);

		AnswerSubmissionResult result = learningAnswerService.submit(
			SESSION_ID,
			SESSION_QUESTION_ID,
			SPEECH_ANSWER_ID
		);

		assertEquals(ANSWER_TEXT, result.answer().getAnswerText());
		assertSame(speechAnswer, result.answer().getSpeechAnswer());
		assertEquals(1, result.answer().getAttemptNo());
		assertTrue(result.answer().isCorrect());
		assertSame(assessment, result.assessment());
		verify(answerRepository).save(result.answer());
	}

	@Test
	void 부분_정답_평가_결과와_동적_점수를_저장한다() {
		SpeechAnswer speechAnswer = completedSpeechAnswer(currentQuestion);
		prepareSession();
		when(speechAnswerRepository.findById(SPEECH_ANSWER_ID))
			.thenReturn(Optional.of(speechAnswer));
		when(
			answerRepository
				.findFirstBySessionQuestion_IdOrderByAttemptNoDesc(
					SESSION_QUESTION_ID
				)
		).thenReturn(Optional.empty());
		when(answerRepository.save(any(Answer.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
		AnswerAssessment assessment = new AnswerAssessment(
			true,
			40,
			23,
			15,
			List.of("running"),
			List.of("the boy"),
			"동작 표현은 좋았어요. 주어를 함께 말해 보세요."
		);
		when(answerAssessmentService.assess(
			currentQuestion.getQuestion(),
			ANSWER_TEXT
		)).thenReturn(assessment);

		AnswerSubmissionResult result = learningAnswerService.submit(
			SESSION_ID,
			SESSION_QUESTION_ID,
			SPEECH_ANSWER_ID
		);

		assertEquals(AnswerResult.PARTIALLY_CORRECT, result.answer().getResult());
		assertEquals(78, result.answer().getScore());
		assertSame(assessment, result.assessment());
		assertTrue(result.canRetry());
		assertEquals(1, result.remainingAttempts());
	}

	@Test
	void 존재하지_않는_음성_답변은_거부한다() {
		prepareSession();
		when(speechAnswerRepository.findById(SPEECH_ANSWER_ID))
			.thenReturn(Optional.empty());

		assertThrows(
			SpeechAnswerNotFoundException.class,
			() -> learningAnswerService.submit(
				SESSION_ID,
				SESSION_QUESTION_ID,
				SPEECH_ANSWER_ID
			)
		);
		verifyNoInteractions(answerRepository);
	}

	@Test
	void 다른_문제의_음성_답변은_거부한다() {
		LearningSession otherSession = createSession();
		SpeechAnswer speechAnswer = completedSpeechAnswer(
			otherSession.getCurrentQuestion()
		);
		prepareSession();
		when(speechAnswerRepository.findById(SPEECH_ANSWER_ID))
			.thenReturn(Optional.of(speechAnswer));

		assertThrows(
			CurrentQuestionMismatchException.class,
			() -> learningAnswerService.submit(
				SESSION_ID,
				SESSION_QUESTION_ID,
				SPEECH_ANSWER_ID
			)
		);
		verifyNoInteractions(answerRepository);
	}

	@Test
	void 처리가_완료되지_않은_음성_답변은_거부한다() {
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			currentQuestion,
			"request-key",
			1
		);
		ReflectionTestUtils.setField(
			speechAnswer,
			"id",
			SPEECH_ANSWER_ID
		);
		prepareSession();
		when(speechAnswerRepository.findById(SPEECH_ANSWER_ID))
			.thenReturn(Optional.of(speechAnswer));

		assertThrows(
			IllegalStateException.class,
			() -> learningAnswerService.submit(
				SESSION_ID,
				SESSION_QUESTION_ID,
				SPEECH_ANSWER_ID
			)
		);
		verifyNoInteractions(answerRepository);
	}

	@Test
	void 이미_제출에_사용한_음성_답변은_거부한다() {
		SpeechAnswer speechAnswer = completedSpeechAnswer(currentQuestion);
		prepareSession();
		when(speechAnswerRepository.findById(SPEECH_ANSWER_ID))
			.thenReturn(Optional.of(speechAnswer));
		when(answerRepository.existsBySpeechAnswer_Id(SPEECH_ANSWER_ID))
			.thenReturn(true);

		assertThrows(
			IllegalStateException.class,
			() -> learningAnswerService.submit(
				SESSION_ID,
				SESSION_QUESTION_ID,
				SPEECH_ANSWER_ID
			)
		);
		verifyNoInteractions(answerAssessmentService);
	}

	private AnswerAssessment correctAssessment() {
		return new AnswerAssessment(
			true,
			50,
			30,
			20,
			List.of("is running"),
			List.of(),
			"현재진행형을 정확하게 사용했어요!"
		);
	}

	private void prepareSession() {
		when(learningSessionRepository.findForUpdateById(SESSION_ID))
			.thenReturn(Optional.of(session));
	}

	private SpeechAnswer completedSpeechAnswer(
		LearningSessionQuestion sessionQuestion
	) {
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			sessionQuestion,
			"request-key",
			1
		);
		ReflectionTestUtils.setField(
			speechAnswer,
			"id",
			SPEECH_ANSWER_ID
		);
		speechAnswer.complete(ANSWER_TEXT, 0.94, "TEST_STT");
		return speechAnswer;
	}

	private LearningSession createSession() {
		Question question = Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"소년은 무엇을 하고 있나요?",
			null,
			ANSWER_TEXT,
			Set.of(ANSWER_TEXT),
			null,
			"https://example.com/question.mp3"
		);
		return LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(question)
		);
	}
}
