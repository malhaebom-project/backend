package com.malhaebom.malhaebom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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
import com.malhaebom.malhaebom.global.exception.AiRequestLimitExceededException;
import com.malhaebom.malhaebom.global.exception.CurrentQuestionMismatchException;
import com.malhaebom.malhaebom.global.exception.SpeechNotRecognizedException;
import com.malhaebom.malhaebom.global.exception.SpeechProcessingFailedException;
import com.malhaebom.malhaebom.global.exception.SpeechTranscriptionTimeoutException;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerResult;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

@ExtendWith(MockitoExtension.class)
class SpeechAnswerServiceTest {

	private static final Long SESSION_ID = 10L;
	private static final Long SESSION_QUESTION_ID = 20L;
	private static final Long SPEECH_ANSWER_ID = 30L;
	private static final String REQUEST_KEY =
		"e23b37e7-d7d4-407e-9f54-dcdaee508799";
	private static final String STT_PROVIDER = "AMAZON_TRANSCRIBE";
	private static final SpeechAudio AUDIO = new SpeechAudio(
		new byte[] {1, 2, 3},
		"audio/webm;codecs=opus"
	);

	@Mock
	private SpeechAnswerStateService stateService;

	private FakeSpeechTranscriber transcriber;
	private SpeechAnswerService speechAnswerService;

	@BeforeEach
	void setUp() {
		transcriber = new FakeSpeechTranscriber();
		speechAnswerService = new SpeechAnswerService(
			stateService,
			transcriber
		);
	}

	@Test
	void 가짜_STT_결과를_저장하고_반환한다() {
		SpeechAnswer processing = createSpeechAnswer(false);
		SpeechAnswer completed = createSpeechAnswer(true);
		SpeechTranscriptionResult transcription =
			new SpeechTranscriptionResult(
				"He is running.",
				0.94,
				STT_PROVIDER
			);
		transcriber.willReturn(transcription);
		when(
			stateService.start(
				SESSION_ID,
				SESSION_QUESTION_ID,
				REQUEST_KEY
			)
		).thenReturn(processing);
		when(
			stateService.complete(
				SPEECH_ANSWER_ID,
				"He is running.",
				0.94,
				STT_PROVIDER
			)
		).thenReturn(completed);

		SpeechAnswerResult result = speechAnswerService.upload(
			SESSION_ID,
			SESSION_QUESTION_ID,
			REQUEST_KEY,
			AUDIO
		);

		assertEquals(SPEECH_ANSWER_ID, result.speechAnswerId());
		assertEquals("He is running.", result.transcript());
		assertEquals(0.94, result.confidence());
		assertEquals(1, transcriber.callCount);
		assertEquals(SPEECH_ANSWER_ID, transcriber.speechAnswerId);
		assertEquals(REQUEST_KEY, transcriber.requestKey);
		assertSame(AUDIO, transcriber.audio);
	}

	@Test
	void 완료된_멱등_요청은_STT를_다시_호출하지_않는다() {
		SpeechAnswer completed = createSpeechAnswer(true);
		when(
			stateService.start(
				SESSION_ID,
				SESSION_QUESTION_ID,
				REQUEST_KEY
			)
		).thenReturn(completed);

		SpeechAnswerResult result = speechAnswerService.upload(
			SESSION_ID,
			SESSION_QUESTION_ID,
			REQUEST_KEY,
			AUDIO
		);

		assertEquals(SPEECH_ANSWER_ID, result.speechAnswerId());
		assertEquals("He is running.", result.transcript());
		assertEquals(0, transcriber.callCount);
		verify(
			stateService,
			never()
		).complete(
			SPEECH_ANSWER_ID,
			"He is running.",
			0.94,
			STT_PROVIDER
		);
	}

	@Test
	void 빈_변환_결과는_실패로_기록하고_인식_실패를_반환한다() {
		SpeechAnswer processing = createSpeechAnswer(false);
		transcriber.willReturn(
			new SpeechTranscriptionResult("  ", null, STT_PROVIDER)
		);
		when(
			stateService.start(
				SESSION_ID,
				SESSION_QUESTION_ID,
				REQUEST_KEY
			)
		).thenReturn(processing);

		assertThrows(
			SpeechNotRecognizedException.class,
			() -> upload()
		);

		verify(stateService).fail(
			SPEECH_ANSWER_ID,
			"인식된 발화가 없습니다.",
			STT_PROVIDER
		);
	}

	@Test
	void STT_타임아웃은_실패로_기록하고_원래_예외를_반환한다() {
		SpeechAnswer processing = createSpeechAnswer(false);
		SpeechTranscriptionTimeoutException timeout =
			new SpeechTranscriptionTimeoutException();
		transcriber.willThrow(timeout);
		when(
			stateService.start(
				SESSION_ID,
				SESSION_QUESTION_ID,
				REQUEST_KEY
			)
		).thenReturn(processing);

		SpeechTranscriptionTimeoutException thrown = assertThrows(
			SpeechTranscriptionTimeoutException.class,
			() -> upload()
		);

		assertSame(timeout, thrown);
		verify(stateService).fail(
			SPEECH_ANSWER_ID,
			"STT 처리 시간이 초과되었습니다.",
			STT_PROVIDER
		);
	}

	@Test
	void STT_요청_제한은_실패로_기록하고_429_예외를_유지한다() {
		SpeechAnswer processing = createSpeechAnswer(false);
		AiRequestLimitExceededException requestLimit =
			new AiRequestLimitExceededException();
		transcriber.willThrow(requestLimit);
		when(
			stateService.start(
				SESSION_ID,
				SESSION_QUESTION_ID,
				REQUEST_KEY
			)
		).thenReturn(processing);

		AiRequestLimitExceededException thrown = assertThrows(
			AiRequestLimitExceededException.class,
			() -> upload()
		);

		assertSame(requestLimit, thrown);
		verify(stateService).fail(
			SPEECH_ANSWER_ID,
			"STT 요청 제한을 초과했습니다.",
			STT_PROVIDER
		);
	}

	@Test
	void 예상하지_못한_STT_오류는_안전한_실패_정보만_저장한다() {
		SpeechAnswer processing = createSpeechAnswer(false);
		RuntimeException awsException =
			new RuntimeException("secret bucket/key and AWS response");
		transcriber.willThrow(awsException);
		when(
			stateService.start(
				SESSION_ID,
				SESSION_QUESTION_ID,
				REQUEST_KEY
			)
		).thenReturn(processing);

		SpeechProcessingFailedException thrown = assertThrows(
			SpeechProcessingFailedException.class,
			() -> upload()
		);

		assertSame(awsException, thrown.getCause());
		verify(stateService).fail(
			SPEECH_ANSWER_ID,
			"STT 처리에 실패했습니다.",
			STT_PROVIDER
		);
	}

	@Test
	void 현재_문제가_아니면_STT를_호출하지_않는다() {
		when(
			stateService.start(
				SESSION_ID,
				SESSION_QUESTION_ID,
				REQUEST_KEY
			)
		).thenThrow(new CurrentQuestionMismatchException());

		assertThrows(
			CurrentQuestionMismatchException.class,
			() -> upload()
		);

		assertEquals(0, transcriber.callCount);
	}

	private SpeechAnswerResult upload() {
		return speechAnswerService.upload(
			SESSION_ID,
			SESSION_QUESTION_ID,
			REQUEST_KEY,
			AUDIO
		);
	}

	private SpeechAnswer createSpeechAnswer(boolean completed) {
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			createSessionQuestion(),
			REQUEST_KEY,
			1
		);
		ReflectionTestUtils.setField(
			speechAnswer,
			"id",
			SPEECH_ANSWER_ID
		);
		if (completed) {
			speechAnswer.complete(
				"He is running.",
				0.94,
				STT_PROVIDER
			);
		}
		return speechAnswer;
	}

	private LearningSessionQuestion createSessionQuestion() {
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
		LearningSession session = LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(question)
		);

		return session.getCurrentQuestion();
	}

	private static class FakeSpeechTranscriber
		implements SpeechTranscriber {

		private SpeechTranscriptionResult result;
		private RuntimeException exception;
		private int callCount;
		private Long speechAnswerId;
		private String requestKey;
		private SpeechAudio audio;

		void willReturn(SpeechTranscriptionResult result) {
			this.result = result;
			this.exception = null;
		}

		void willThrow(RuntimeException exception) {
			this.exception = exception;
			this.result = null;
		}

		@Override
		public SpeechTranscriptionResult transcribe(
			Long speechAnswerId,
			String requestKey,
			SpeechAudio audio
		) {
			callCount++;
			this.speechAnswerId = speechAnswerId;
			this.requestKey = requestKey;
			this.audio = audio;

			if (exception != null) {
				throw exception;
			}
			return result;
		}
	}
}
