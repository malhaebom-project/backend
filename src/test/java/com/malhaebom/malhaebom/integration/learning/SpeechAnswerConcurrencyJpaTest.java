package com.malhaebom.malhaebom.integration.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.SpeechProcessingStatus;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ApiExceptionHandler;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.async.AsyncConfiguration;
import com.malhaebom.malhaebom.infra.async.SpeechAnswerAsyncProperties;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.infra.speech.SpeechTranscriptionConcurrencyLimiter;
import com.malhaebom.malhaebom.presentation.LearningSpeechController;
import com.malhaebom.malhaebom.service.SpeechAnswerService;
import com.malhaebom.malhaebom.service.SpeechAnswerStateService;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerResult;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerTask;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionTask;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
	JpaAuditingConfiguration.class,
	SpeechAnswerStateService.class,
	SpeechAnswerService.class,
	SpeechAnswerConcurrencyJpaTest.SpeechTestConfiguration.class
})
class SpeechAnswerConcurrencyJpaTest {

	private static final int MAX_CONCURRENT_REQUESTS = 8;
	private static final String ENDPOINT =
		"/api/v1/learning-sessions/{sessionId}/questions/"
			+ "{sessionQuestionId}/speech";
	private static final SpeechAudio AUDIO = new SpeechAudio(
		new byte[] {1, 2, 3},
		"audio/webm"
	);

	@Autowired
	private LearningSessionRepository learningSessionRepository;
	@Autowired
	private QuestionRepository questionRepository;
	@Autowired
	private SpeechAnswerRepository speechAnswerRepository;
	@Autowired
	private SpeechAnswerService speechAnswerService;
	@Autowired
	private ControllableSpeechTranscriber transcriber;
	@Autowired
	private SpeechAnswerAsyncProperties asyncProperties;

	private String requestPrefix;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		requestPrefix = UUID.randomUUID().toString();
		transcriber.reset();
		mockMvc = MockMvcBuilders.standaloneSetup(
			new LearningSpeechController(speechAnswerService, asyncProperties)
		)
			.setControllerAdvice(new ApiExceptionHandler())
			.build();
	}

	@AfterEach
	void tearDown() {
		transcriber.completeAll();
	}

	@Test
	void 미완료_8건이_있으면_9번째를_Google에_보내지_않고_503으로_거절한다()
		throws Exception {
		LearningSession session = saveSession();
		Long sessionQuestionId = session.getCurrentQuestion().getId();
		List<MvcResult> active = new ArrayList<>();
		for (int index = 0; index < MAX_CONCURRENT_REQUESTS; index++) {
			active.add(startUpload(
				session,
				sessionQuestionId,
				"active-" + index
			));
		}
		assertTrue(transcriber.allPending());

		String rejectedKey = requestKey("rejected");
		MvcResult rejected = startUpload(
			session,
			sessionQuestionId,
			"rejected"
		);
		mockMvc.perform(asyncDispatch(rejected))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.errorCode")
				.value("STT_PROCESSING_OVERLOADED"));
		assertEquals(MAX_CONCURRENT_REQUESTS, transcriber.callCount());
		SpeechAnswer failed = speechAnswerRepository
			.findByRequestKey(rejectedKey)
			.orElseThrow();
		assertEquals(SpeechProcessingStatus.FAILED, failed.getProcessingStatus());
		assertEquals(
			ErrorCode.STT_PROCESSING_OVERLOADED.getMessage(),
			failed.getFailureMessage()
		);

		assertTrue(transcriber.complete(0));
		mockMvc.perform(asyncDispatch(active.getFirst()))
			.andExpect(status().isOk());
		MvcResult accepted = startUpload(
			session,
			sessionQuestionId,
			"accepted-after-completion"
		);

		assertEquals(MAX_CONCURRENT_REQUESTS + 1, transcriber.callCount());
		assertTrue(accepted.getRequest().isAsyncStarted());
	}

	@Test
	void STT_예외_경로에서도_permit을_복구해_새_요청을_받는다() {
		LearningSession session = saveSession();
		Long sessionQuestionId = session.getCurrentQuestion().getId();
		List<SpeechAnswerTask> active = java.util.stream.IntStream
			.range(0, MAX_CONCURRENT_REQUESTS)
			.mapToObj(index -> upload(
				session,
				sessionQuestionId,
				"failure-active-" + index
			))
			.toList();

		assertTrue(transcriber.fail(
			0,
			new RuntimeException("provider secret response")
		));
		ApiException failure = assertThrows(
			ApiException.class,
			() -> await(active.getFirst())
		);
		assertEquals(ErrorCode.STT_PROCESSING_FAILED, failure.getErrorCode());

		SpeechAnswerTask accepted = upload(
			session,
			sessionQuestionId,
			"accepted-after-failure"
		);

		assertEquals(MAX_CONCURRENT_REQUESTS + 1, transcriber.callCount());
		assertFalse(accepted.result().toCompletableFuture().isDone());
		SpeechAnswer failed = speechAnswerRepository
			.findByRequestKey(requestKey("failure-active-0"))
			.orElseThrow();
		assertEquals(SpeechProcessingStatus.FAILED, failed.getProcessingStatus());
		assertEquals(
			ErrorCode.STT_PROCESSING_FAILED.getMessage(),
			failed.getFailureMessage()
		);
	}

	private LearningSession saveSession() {
		return LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			"He is ____ing."
		);
	}

	private SpeechAnswerTask upload(
		LearningSession session,
		Long sessionQuestionId,
		String requestSuffix
	) {
		return speechAnswerService.uploadAsync(
			session.getId(),
			sessionQuestionId,
			requestKey(requestSuffix),
			AUDIO
		);
	}

	private MvcResult startUpload(
		LearningSession session,
		Long sessionQuestionId,
		String requestSuffix
	) throws Exception {
		return mockMvc.perform(
			multipart(ENDPOINT, session.getId(), sessionQuestionId)
				.file(new MockMultipartFile(
					"audio",
					"answer.webm",
					"audio/webm",
					AUDIO.content()
				))
				.header("Idempotency-Key", requestKey(requestSuffix))
		)
			.andExpect(request().asyncStarted())
			.andReturn();
	}

	private String requestKey(String suffix) {
		return requestPrefix + "-" + suffix;
	}

	private SpeechAnswerResult await(SpeechAnswerTask task) {
		try {
			return task.result().toCompletableFuture().join();
		} catch (CompletionException exception) {
			if (exception.getCause() instanceof RuntimeException cause) {
				throw cause;
			}
			throw exception;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SpeechTestConfiguration {

		@Bean
		SpeechAnswerAsyncProperties speechAnswerAsyncProperties() {
			return new SpeechAnswerAsyncProperties(
				Duration.ofSeconds(20),
				MAX_CONCURRENT_REQUESTS,
				Duration.ofSeconds(60)
			);
		}

		@Bean
		SpeechTranscriptionConcurrencyLimiter concurrencyLimiter(
			SpeechAnswerAsyncProperties properties
		) {
			return new SpeechTranscriptionConcurrencyLimiter(properties);
		}

		@Bean
		ControllableSpeechTranscriber speechTranscriber() {
			return new ControllableSpeechTranscriber();
		}

		@Bean(name = AsyncConfiguration.SPEECH_COMPLETION_EXECUTOR)
		Executor speechCompletionExecutor() {
			return Runnable::run;
		}
	}

	private static final class ControllableSpeechTranscriber
		implements SpeechTranscriber {

		private final List<CompletableFuture<SpeechTranscriptionResult>> requests =
			new CopyOnWriteArrayList<>();

		void reset() {
			requests.clear();
		}

		int callCount() {
			return requests.size();
		}

		boolean allPending() {
			return requests.stream().noneMatch(CompletableFuture::isDone);
		}

		boolean complete(int index) {
			return requests.get(index).complete(successResult());
		}

		boolean fail(int index, RuntimeException exception) {
			return requests.get(index).completeExceptionally(exception);
		}

		void completeAll() {
			requests.forEach(request -> request.complete(successResult()));
		}

		@Override
		public String provider() {
			return "TEST_STT";
		}

		@Override
		public SpeechTranscriptionTask transcribeAsync(
			SpeechAudio audio,
			List<String> adaptationPhrases
		) {
			CompletableFuture<SpeechTranscriptionResult> result =
				new CompletableFuture<>();
			requests.add(result);
			return new SpeechTranscriptionTask(
				result,
				() -> result.cancel(true)
			);
		}

		private static SpeechTranscriptionResult successResult() {
			return new SpeechTranscriptionResult(
				"He is running.",
				0.94,
				"TEST_STT"
			);
		}
	}
}
