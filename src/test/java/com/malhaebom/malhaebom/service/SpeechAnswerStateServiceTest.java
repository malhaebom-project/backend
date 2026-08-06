package com.malhaebom.malhaebom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.SpeechProcessingStatus;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.global.exception.CurrentQuestionMismatchException;
import com.malhaebom.malhaebom.global.exception.SpeechAnswerNotFoundException;
import com.malhaebom.malhaebom.global.exception.SpeechProcessingException;
import com.malhaebom.malhaebom.global.exception.SpeechProcessingFailedException;

@ExtendWith(MockitoExtension.class)
class SpeechAnswerStateServiceTest {

	private static final Long SESSION_ID = 10L;
	private static final Long SESSION_QUESTION_ID = 20L;
	private static final Long SPEECH_ANSWER_ID = 30L;
	private static final String REQUEST_KEY =
		"e23b37e7-d7d4-407e-9f54-dcdaee508799";
	private static final String STT_PROVIDER = "GOOGLE_CLOUD_STT_V2";

	@Mock
	private LearningSessionRepository learningSessionRepository;

	@Mock
	private SpeechAnswerRepository speechAnswerRepository;

	private SpeechAnswerStateService stateService;
	private LearningSession session;
	private LearningSessionQuestion currentQuestion;

	@BeforeEach
	void setUp() {
		stateService = new SpeechAnswerStateService(
			learningSessionRepository,
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
	void 현재_문제에_첫_음성_답변을_생성한다() {
		prepareSession();
		when(speechAnswerRepository.findByRequestKey(REQUEST_KEY))
			.thenReturn(Optional.empty());
		when(
			speechAnswerRepository
				.findFirstBySessionQuestion_IdOrderByRecordingNoDesc(
					SESSION_QUESTION_ID
				)
		).thenReturn(Optional.empty());
		when(speechAnswerRepository.saveAndFlush(any(SpeechAnswer.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		SpeechAnswer started = stateService.start(
			SESSION_ID,
			SESSION_QUESTION_ID,
			REQUEST_KEY
		);

		assertSame(currentQuestion, started.getSessionQuestion());
		assertEquals(REQUEST_KEY, started.getRequestKey());
		assertEquals(1, started.getRecordingNo());
		assertEquals(
			SpeechProcessingStatus.PROCESSING,
			started.getProcessingStatus()
		);
	}

	@Test
	void 다시_녹음하면_다음_녹음_순번을_할당한다() {
		prepareSession();
		SpeechAnswer previous = SpeechAnswer.start(
			currentQuestion,
			"previous-request-key",
			2
		);
		when(speechAnswerRepository.findByRequestKey(REQUEST_KEY))
			.thenReturn(Optional.empty());
		when(
			speechAnswerRepository
				.findFirstBySessionQuestion_IdOrderByRecordingNoDesc(
					SESSION_QUESTION_ID
				)
		).thenReturn(Optional.of(previous));
		when(speechAnswerRepository.saveAndFlush(any(SpeechAnswer.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		SpeechAnswer started = stateService.start(
			SESSION_ID,
			SESSION_QUESTION_ID,
			REQUEST_KEY
		);

		assertEquals(3, started.getRecordingNo());
	}

	@Test
	void 완료된_멱등_요청은_저장된_결과를_반환한다() {
		prepareSession();
		SpeechAnswer completed = createSpeechAnswer(REQUEST_KEY);
		completed.complete("He is running.", 0.94, STT_PROVIDER);
		when(speechAnswerRepository.findByRequestKey(REQUEST_KEY))
			.thenReturn(Optional.of(completed));

		SpeechAnswer resolved = stateService.start(
			SESSION_ID,
			SESSION_QUESTION_ID,
			REQUEST_KEY
		);

		assertSame(completed, resolved);
		verify(speechAnswerRepository, never())
			.saveAndFlush(any(SpeechAnswer.class));
	}

	@Test
	void 처리_중인_멱등_요청은_충돌로_거부한다() {
		prepareSession();
		SpeechAnswer processing = createSpeechAnswer(REQUEST_KEY);
		when(speechAnswerRepository.findByRequestKey(REQUEST_KEY))
			.thenReturn(Optional.of(processing));

		assertThrows(
			SpeechProcessingException.class,
			() -> stateService.start(
				SESSION_ID,
				SESSION_QUESTION_ID,
				REQUEST_KEY
			)
		);
	}

	@Test
	void 실패한_멱등_요청은_새_행을_만들지_않는다() {
		prepareSession();
		SpeechAnswer failed = createSpeechAnswer(REQUEST_KEY);
		failed.fail("STT 처리에 실패했습니다.", STT_PROVIDER);
		when(speechAnswerRepository.findByRequestKey(REQUEST_KEY))
			.thenReturn(Optional.of(failed));

		assertThrows(
			SpeechProcessingFailedException.class,
			() -> stateService.start(
				SESSION_ID,
				SESSION_QUESTION_ID,
				REQUEST_KEY
			)
		);
		verify(speechAnswerRepository, never())
			.saveAndFlush(any(SpeechAnswer.class));
	}

	@Test
	void 현재_문제가_아니면_음성_답변_저장소에_접근하지_않는다() {
		prepareSession();

		assertThrows(
			CurrentQuestionMismatchException.class,
			() -> stateService.start(
				SESSION_ID,
				999L,
				REQUEST_KEY
			)
		);

		verifyNoInteractions(speechAnswerRepository);
	}

	@Test
	void 완료한_문제에는_음성_답변을_생성하지_않는다() {
		prepareSession();
		session.completeCurrentQuestion(true);

		assertThrows(
			IllegalStateException.class,
			() -> stateService.start(
				SESSION_ID,
				SESSION_QUESTION_ID,
				REQUEST_KEY
			)
		);

		verifyNoInteractions(speechAnswerRepository);
	}

	@Test
	void 다른_문제의_멱등키는_재사용할_수_없다() {
		prepareSession();
		LearningSessionQuestion otherQuestion =
			createSession().getCurrentQuestion();
		ReflectionTestUtils.setField(otherQuestion, "id", 999L);
		SpeechAnswer existing = SpeechAnswer.start(
			otherQuestion,
			REQUEST_KEY,
			1
		);
		when(speechAnswerRepository.findByRequestKey(REQUEST_KEY))
			.thenReturn(Optional.of(existing));

		assertThrows(
			CurrentQuestionMismatchException.class,
			() -> stateService.start(
				SESSION_ID,
				SESSION_QUESTION_ID,
				REQUEST_KEY
			)
		);
	}

	@Test
	void 처리_결과를_완료_상태로_변경한다() {
		SpeechAnswer processing = createSpeechAnswer(REQUEST_KEY);
		ReflectionTestUtils.setField(
			processing,
			"id",
			SPEECH_ANSWER_ID
		);
		when(speechAnswerRepository.findById(SPEECH_ANSWER_ID))
			.thenReturn(Optional.of(processing));

		SpeechAnswer completed = stateService.complete(
			SPEECH_ANSWER_ID,
			"He is running.",
			0.94,
			STT_PROVIDER
		);

		assertSame(processing, completed);
		assertEquals(
			SpeechProcessingStatus.COMPLETED,
			completed.getProcessingStatus()
		);
		assertEquals("He is running.", completed.getTranscript());
	}

	@Test
	void 존재하지_않는_음성_답변은_전용_예외를_발생시킨다() {
		when(speechAnswerRepository.findById(SPEECH_ANSWER_ID))
			.thenReturn(Optional.empty());

		assertThrows(
			SpeechAnswerNotFoundException.class,
			() -> stateService.complete(
				SPEECH_ANSWER_ID,
				"He is running.",
				0.94,
				STT_PROVIDER
			)
		);
	}

	private void prepareSession() {
		when(learningSessionRepository.findForUpdateById(SESSION_ID))
			.thenReturn(Optional.of(session));
	}

	private SpeechAnswer createSpeechAnswer(String requestKey) {
		return SpeechAnswer.start(currentQuestion, requestKey, 1);
	}

	private LearningSession createSession() {
		Question question = Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"남자아이는 무엇을 하고 있나요?",
			null,
			"The boy is running.",
			Set.of("He is running.", "He's running."),
			"He is ____ing.",
			null
		);
		return LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(question)
		);
	}
}
