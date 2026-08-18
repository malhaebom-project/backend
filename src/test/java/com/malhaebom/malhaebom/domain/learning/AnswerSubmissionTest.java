package com.malhaebom.malhaebom.domain.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AnswerSubmissionTest {
	private static final String PROCESSING_TOKEN =
		"215bf1ca-03dc-4a7a-af56-09ad0cc26a24";
	private static final Instant CLAIMED_AT =
		Instant.parse("2026-08-13T07:00:00Z");
	private static final Instant LEASE_EXPIRES_AT =
		Instant.parse("2026-08-13T07:01:00Z");

	@Test
	void 완료된_음성_답변의_제출을_예약한다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		SpeechAnswer speechAnswer = completedSpeechAnswer(sessionQuestion);

		AnswerSubmission submission = AnswerSubmission.reserve(
			sessionQuestion,
			speechAnswer,
			1
		);

		assertSame(sessionQuestion, submission.getSessionQuestion());
		assertSame(speechAnswer, submission.getSpeechAnswer());
		assertEquals(1, submission.getAttemptNo());
		assertEquals(AnswerSubmissionStatus.PENDING, submission.getStatus());
		assertNull(submission.getAnswer());
		assertNull(submission.getProcessingToken());
		assertNull(submission.getLeaseExpiresAt());
		assertNull(submission.getFailureMessage());
	}

	@Test
	void 처리_중인_음성_답변은_제출을_예약할_수_없다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		SpeechAnswer processing = SpeechAnswer.start(
			sessionQuestion,
			"processing-request-key",
			1
		);

		assertThrows(
			IllegalArgumentException.class,
			() -> AnswerSubmission.reserve(sessionQuestion, processing, 1)
		);
	}

	@Test
	void 다른_문제의_음성_답변은_제출을_예약할_수_없다() {
		LearningSessionQuestion currentQuestion = createSessionQuestion();
		LearningSessionQuestion otherQuestion = createSessionQuestion();

		assertThrows(
			IllegalArgumentException.class,
			() -> AnswerSubmission.reserve(
				currentQuestion,
				completedSpeechAnswer(otherQuestion),
				1
			)
		);
	}

	@Test
	void 완료된_문제는_제출을_예약할_수_없다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		SpeechAnswer speechAnswer = completedSpeechAnswer(sessionQuestion);
		sessionQuestion.getLearningSession().completeCurrentQuestion(true);

		assertThrows(
			IllegalStateException.class,
			() -> AnswerSubmission.reserve(
				sessionQuestion,
				speechAnswer,
				1
			)
		);
	}

	@Test
	void 답변_시도_번호는_1_이상이어야_한다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();

		assertThrows(
			IllegalArgumentException.class,
			() -> AnswerSubmission.reserve(
				sessionQuestion,
				completedSpeechAnswer(sessionQuestion),
				0
			)
		);
	}

	@Test
	void 대기_중인_예약을_처리_토큰과_임대_만료_시각으로_선점한다() {
		AnswerSubmission submission = createSubmission();

		submission.claim(PROCESSING_TOKEN, CLAIMED_AT, LEASE_EXPIRES_AT);

		assertEquals(AnswerSubmissionStatus.PROCESSING, submission.getStatus());
		assertEquals(PROCESSING_TOKEN, submission.getProcessingToken());
		assertEquals(LEASE_EXPIRES_AT, submission.getLeaseExpiresAt());
		assertNull(submission.getFailureMessage());
		assertFalse(submission.isLeaseExpiredAt(CLAIMED_AT));
		assertTrue(submission.isLeaseExpiredAt(LEASE_EXPIRES_AT));
	}

	@Test
	void 임대가_유효한_처리_중_예약은_다시_선점할_수_없다() {
		AnswerSubmission submission = createSubmission();
		submission.claim(PROCESSING_TOKEN, CLAIMED_AT, LEASE_EXPIRES_AT);

		assertThrows(
			IllegalStateException.class,
			() -> submission.claim(
				"3cbafaf0-fd5b-47aa-8d2d-18c7c2a47f0a",
				CLAIMED_AT.plusSeconds(30),
				LEASE_EXPIRES_AT.plusSeconds(30)
			)
		);
	}

	@Test
	void 임대가_만료된_처리_중_예약은_새_토큰으로_재선점한다() {
		AnswerSubmission submission = createSubmission();
		String newToken = "3cbafaf0-fd5b-47aa-8d2d-18c7c2a47f0a";
		submission.claim(PROCESSING_TOKEN, CLAIMED_AT, LEASE_EXPIRES_AT);

		submission.claim(
			newToken,
			LEASE_EXPIRES_AT,
			LEASE_EXPIRES_AT.plusSeconds(60)
		);

		assertEquals(AnswerSubmissionStatus.PROCESSING, submission.getStatus());
		assertEquals(newToken, submission.getProcessingToken());
		assertEquals(
			LEASE_EXPIRES_AT.plusSeconds(60),
			submission.getLeaseExpiresAt()
		);
	}

	@Test
	void 처리_토큰이_일치하면_예약을_완료한다() {
		AnswerSubmission submission = createSubmission();
		submission.claim(PROCESSING_TOKEN, CLAIMED_AT, LEASE_EXPIRES_AT);
		Answer answer = createAnswer(submission);

		submission.complete(PROCESSING_TOKEN, answer);

		assertEquals(AnswerSubmissionStatus.COMPLETED, submission.getStatus());
		assertSame(answer, submission.getAnswer());
		assertNull(submission.getProcessingToken());
		assertNull(submission.getLeaseExpiresAt());
		assertNull(submission.getFailureMessage());
	}

	@Test
	void 재선점_전의_처리_토큰으로는_예약을_완료할_수_없다() {
		AnswerSubmission submission = createSubmission();
		String newToken = "3cbafaf0-fd5b-47aa-8d2d-18c7c2a47f0a";
		submission.claim(PROCESSING_TOKEN, CLAIMED_AT, LEASE_EXPIRES_AT);
		submission.claim(
			newToken,
			LEASE_EXPIRES_AT,
			LEASE_EXPIRES_AT.plusSeconds(60)
		);

		assertThrows(
			IllegalStateException.class,
			() -> submission.complete(
				PROCESSING_TOKEN,
				createAnswer(submission)
			)
		);
	}

	@Test
	void 처리_토큰이_일치하면_예약을_실패_처리한다() {
		AnswerSubmission submission = createSubmission();
		submission.claim(PROCESSING_TOKEN, CLAIMED_AT, LEASE_EXPIRES_AT);

		submission.fail(PROCESSING_TOKEN, "OpenAI 요청 시간이 초과되었습니다.");

		assertEquals(AnswerSubmissionStatus.FAILED, submission.getStatus());
		assertNull(submission.getAnswer());
		assertNull(submission.getProcessingToken());
		assertNull(submission.getLeaseExpiresAt());
		assertEquals(
			"OpenAI 요청 시간이 초과되었습니다.",
			submission.getFailureMessage()
		);
	}

	@Test
	void 실패한_예약은_같은_예약을_대기_상태로_되돌려_재시도한다() {
		AnswerSubmission submission = createSubmission();
		submission.claim(PROCESSING_TOKEN, CLAIMED_AT, LEASE_EXPIRES_AT);
		submission.fail(PROCESSING_TOKEN, "OpenAI 요청 시간이 초과되었습니다.");

		submission.retry();

		assertEquals(AnswerSubmissionStatus.PENDING, submission.getStatus());
		assertNull(submission.getAnswer());
		assertNull(submission.getProcessingToken());
		assertNull(submission.getLeaseExpiresAt());
		assertNull(submission.getFailureMessage());
	}

	@Test
	void 실패하지_않은_예약은_재시도_상태로_되돌릴_수_없다() {
		AnswerSubmission submission = createSubmission();

		assertThrows(IllegalStateException.class, submission::retry);
	}

	@Test
	void 임대_만료_시각은_선점_시각보다_이후여야_한다() {
		AnswerSubmission submission = createSubmission();

		assertThrows(
			IllegalArgumentException.class,
			() -> submission.claim(PROCESSING_TOKEN, CLAIMED_AT, CLAIMED_AT)
		);
	}

	private AnswerSubmission createSubmission() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		return AnswerSubmission.reserve(
			sessionQuestion,
			completedSpeechAnswer(sessionQuestion),
			1
		);
	}

	private Answer createAnswer(AnswerSubmission submission) {
		return Answer.create(
			submission.getSessionQuestion(),
			submission.getSpeechAnswer(),
			submission.getAttemptNo(),
			AnswerEvaluation.from(AnswerResult.CORRECT),
			"현재진행형을 정확하게 사용했어요!"
		);
	}

	private SpeechAnswer completedSpeechAnswer(
		LearningSessionQuestion sessionQuestion
	) {
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			sessionQuestion,
			"request-key",
			1
		);
		speechAnswer.complete("He is running.", 0.94, "TEST_STT");
		return speechAnswer;
	}

	private LearningSessionQuestion createSessionQuestion() {
		Question question = createQuestion("What is the boy doing?");
		LearningSession session = LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(question)
		);
		return session.getCurrentQuestion();
	}

	private Question createQuestion(String questionText) {
		return Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			questionText,
			"남자아이는 무엇을 하고 있나요?",
			null,
			"The boy is running.",
			Set.of("He is running.", "He's running."),
			null,
			null
		);
	}
}
