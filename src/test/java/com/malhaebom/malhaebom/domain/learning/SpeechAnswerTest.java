package com.malhaebom.malhaebom.domain.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SpeechAnswerTest {

	private static final String REQUEST_KEY =
		"e23b37e7-d7d4-407e-9f54-dcdaee508799";
	private static final String STT_PROVIDER = "AMAZON_TRANSCRIBE";

	@Test
	void 음성_답변을_처리_중_상태로_생성한다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();

		SpeechAnswer speechAnswer = SpeechAnswer.start(
			sessionQuestion,
			REQUEST_KEY,
			1
		);

		assertSame(sessionQuestion, speechAnswer.getSessionQuestion());
		assertEquals(REQUEST_KEY, speechAnswer.getRequestKey());
		assertEquals(1, speechAnswer.getRecordingNo());
		assertEquals(
			SpeechProcessingStatus.PROCESSING,
			speechAnswer.getProcessingStatus()
		);
		assertNull(speechAnswer.getTranscript());
		assertNull(speechAnswer.getConfidence());
		assertNull(speechAnswer.getSttProvider());
		assertNull(speechAnswer.getFailureMessage());
		assertNotNull(speechAnswer.getCreatedAt());
		assertEquals(speechAnswer.getCreatedAt(), speechAnswer.getUpdatedAt());
		assertFalse(speechAnswer.isCompleted());
	}

	@Test
	void 녹음_순번은_1_이상이어야_한다() {
		assertThrows(
			IllegalArgumentException.class,
			() -> SpeechAnswer.start(
				createSessionQuestion(),
				REQUEST_KEY,
				0
			)
		);
	}

	@Test
	void 세션_문제와_요청_키가_없으면_생성할_수_없다() {
		assertThrows(
			IllegalArgumentException.class,
			() -> SpeechAnswer.start(null, REQUEST_KEY, 1)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> SpeechAnswer.start(createSessionQuestion(), " ", 1)
		);
	}

	@Test
	void 완료한_문제에는_음성_답변을_생성할_수_없다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		sessionQuestion.getLearningSession().completeCurrentQuestion(true);

		assertThrows(
			IllegalStateException.class,
			() -> SpeechAnswer.start(sessionQuestion, REQUEST_KEY, 1)
		);
	}

	@Test
	void 처리_중인_음성_답변을_완료한다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			sessionQuestion,
			REQUEST_KEY,
			1
		);

		speechAnswer.complete(
			"He is running.",
			0.94,
			STT_PROVIDER
		);

		assertEquals(
			SpeechProcessingStatus.COMPLETED,
			speechAnswer.getProcessingStatus()
		);
		assertEquals("He is running.", speechAnswer.getTranscript());
		assertEquals(0.94, speechAnswer.getConfidence());
		assertEquals(STT_PROVIDER, speechAnswer.getSttProvider());
		assertNull(speechAnswer.getFailureMessage());
		assertTrue(speechAnswer.isCompleted());
		assertTrue(speechAnswer.isUsableFor(sessionQuestion));
		assertFalse(speechAnswer.isUsableFor(createSessionQuestion()));
		assertFalse(speechAnswer.getUpdatedAt().isBefore(speechAnswer.getCreatedAt()));
	}

	@Test
	void 신뢰도_데이터가_없어도_완료할_수_있다() {
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			createSessionQuestion(),
			REQUEST_KEY,
			1
		);

		speechAnswer.complete("He is running.", null, STT_PROVIDER);

		assertTrue(speechAnswer.isCompleted());
		assertNull(speechAnswer.getConfidence());
	}

	@Test
	void 신뢰도는_0에서_1_사이만_허용한다() {
		assertThrows(
			IllegalArgumentException.class,
			() -> SpeechAnswer.start(
				createSessionQuestion(),
				REQUEST_KEY,
				1
			).complete(
				"He is running.",
				-0.0001,
				STT_PROVIDER
			)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> SpeechAnswer.start(
				createSessionQuestion(),
				REQUEST_KEY,
				1
			).complete(
				"He is running.",
				1.0001,
				STT_PROVIDER
			)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> SpeechAnswer.start(
				createSessionQuestion(),
				REQUEST_KEY,
				1
			).complete(
				"He is running.",
				Double.NaN,
				STT_PROVIDER
			)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> SpeechAnswer.start(
				createSessionQuestion(),
				REQUEST_KEY,
				1
			).complete(
				"He is running.",
				Double.POSITIVE_INFINITY,
				STT_PROVIDER
			)
		);
	}

	@Test
	void 처리_중인_음성_답변을_실패로_변경한다() {
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			createSessionQuestion(),
			REQUEST_KEY,
			1
		);

		speechAnswer.fail("STT 처리 시간이 초과되었습니다.", STT_PROVIDER);

		assertEquals(
			SpeechProcessingStatus.FAILED,
			speechAnswer.getProcessingStatus()
		);
		assertEquals(
			"STT 처리 시간이 초과되었습니다.",
			speechAnswer.getFailureMessage()
		);
		assertEquals(STT_PROVIDER, speechAnswer.getSttProvider());
		assertNull(speechAnswer.getTranscript());
		assertFalse(speechAnswer.isCompleted());
	}

	@Test
	void 완료_상태에서는_다시_상태를_변경할_수_없다() {
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			createSessionQuestion(),
			REQUEST_KEY,
			1
		);
		speechAnswer.complete("He is running.", 1.0, STT_PROVIDER);

		assertThrows(
			IllegalStateException.class,
			() -> speechAnswer.fail("다시 실패 처리합니다.", STT_PROVIDER)
		);
		assertThrows(
			IllegalStateException.class,
			() -> speechAnswer.complete(
				"He is running.",
				1.0,
				STT_PROVIDER
			)
		);
	}

	@Test
	void 실패_상태에서는_다시_상태를_변경할_수_없다() {
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			createSessionQuestion(),
			REQUEST_KEY,
			1
		);
		speechAnswer.fail("STT 처리에 실패했습니다.", STT_PROVIDER);

		assertThrows(
			IllegalStateException.class,
			() -> speechAnswer.complete(
				"He is running.",
				1.0,
				STT_PROVIDER
			)
		);
		assertThrows(
			IllegalStateException.class,
			() -> speechAnswer.fail("다시 실패 처리합니다.", STT_PROVIDER)
		);
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
}
