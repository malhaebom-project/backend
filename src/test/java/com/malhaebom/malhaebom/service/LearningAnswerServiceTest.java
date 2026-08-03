package com.malhaebom.malhaebom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	private LearningAnswerService learningAnswerService;
	private LearningSession session;
	private LearningSessionQuestion currentQuestion;

	@BeforeEach
	void setUp() {
		learningAnswerService = new LearningAnswerService(
			learningSessionRepository,
			answerRepository,
			speechAnswerRepository
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

		AnswerSubmissionResult result = learningAnswerService.submit(
			SESSION_ID,
			SESSION_QUESTION_ID,
			SPEECH_ANSWER_ID,
			ANSWER_TEXT
		);

		assertEquals(ANSWER_TEXT, result.answer().getAnswerText());
		assertEquals(1, result.answer().getAttemptNo());
		assertTrue(result.answer().isCorrect());
		verify(answerRepository).save(result.answer());
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
				SPEECH_ANSWER_ID,
				ANSWER_TEXT
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
				SPEECH_ANSWER_ID,
				ANSWER_TEXT
			)
		);
		verifyNoInteractions(answerRepository);
	}

	@Test
	void 음성_인식_결과와_다른_답변_텍스트는_거부한다() {
		SpeechAnswer speechAnswer = completedSpeechAnswer(currentQuestion);
		prepareSession();
		when(speechAnswerRepository.findById(SPEECH_ANSWER_ID))
			.thenReturn(Optional.of(speechAnswer));

		assertThrows(
			IllegalArgumentException.class,
			() -> learningAnswerService.submit(
				SESSION_ID,
				SESSION_QUESTION_ID,
				SPEECH_ANSWER_ID,
				"Different answer."
			)
		);
		verifyNoInteractions(answerRepository);
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
