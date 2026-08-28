package com.malhaebom.malhaebom.integration.learning;

import static com.malhaebom.malhaebom.support.SpeechAnswerTestQueries.findByRequestKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.bucket4j.TimeMeter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
import com.malhaebom.malhaebom.infra.observability.ProviderRateLimitMetricsRecorder;
import com.malhaebom.malhaebom.infra.speech.GoogleSpeechRateLimitProperties;
import com.malhaebom.malhaebom.service.policy.SpeechTranscriptionConcurrencyPolicy;
import com.malhaebom.malhaebom.infra.speech.SpeechTranscriptionRateLimiter;
import com.malhaebom.malhaebom.presentation.LearningSpeechController;
import com.malhaebom.malhaebom.presentation.config.SpeechRequestTimeout;
import com.malhaebom.malhaebom.service.speech.InFlightSpeechAnswerRegistry;
import com.malhaebom.malhaebom.service.speech.SpeechAnswerLifecycle;
import com.malhaebom.malhaebom.service.speech.SpeechAnswerCoordinator;
import com.malhaebom.malhaebom.service.speech.SpeechAnswerStateService;
import com.malhaebom.malhaebom.service.ChildProfileService;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerResult;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerStartResult;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerTask;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerRequest;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionTask;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;
import com.malhaebom.malhaebom.service.port.SpeechTranscriptionRateLimit;
import com.malhaebom.malhaebom.service.policy.SpeechProcessingLease;
import com.malhaebom.malhaebom.service.policy.SpeechShutdownPolicy;
import com.malhaebom.malhaebom.support.StubLoginUserArgumentResolver;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
	JpaAuditingConfiguration.class,
	SpeechAnswerStateService.class,
	SpeechAnswerCoordinator.class,
	InFlightSpeechAnswerRegistry.class,
	SpeechAnswerLifecycle.class,
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
	private SpeechAnswerCoordinator speechAnswerCoordinator;
	@Autowired
	private SpeechAnswerStateService stateService;
	@Autowired
	private ControllableSpeechTranscriber transcriber;
	@Autowired
	private SpeechAnswerAsyncProperties asyncProperties;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@MockitoBean
	private ChildProfileService childProfileService;

	private String requestPrefix;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		requestPrefix = UUID.randomUUID().toString();
		transcriber.reset();
		mockMvc = MockMvcBuilders.standaloneSetup(
			new LearningSpeechController(
				speechAnswerCoordinator,
				new SpeechRequestTimeout(asyncProperties.requestTimeout())
			)
		)
			.setCustomArgumentResolvers(
				new StubLoginUserArgumentResolver(LearningJpaTestFixture.USER_ID)
			)
			.setControllerAdvice(new ApiExceptionHandler())
			.build();
	}

	@AfterEach
	void tearDown() {
		transcriber.releaseCancellation();
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
		SpeechAnswer failed = findByRequestKey(speechAnswerRepository, rejectedKey)
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

		RuntimeException providerFailure = new RuntimeException(
			"provider secret response"
		);
		assertTrue(transcriber.fail(0, providerFailure));
		RuntimeException failure = assertThrows(
			RuntimeException.class,
			() -> await(active.getFirst())
		);
		assertSame(providerFailure, failure);

		SpeechAnswerTask accepted = upload(
			session,
			sessionQuestionId,
			"accepted-after-failure"
		);

		assertEquals(MAX_CONCURRENT_REQUESTS + 1, transcriber.callCount());
		assertFalse(accepted.result().toCompletableFuture().isDone());
		SpeechAnswer failed = findByRequestKey(speechAnswerRepository, requestKey("failure-active-0"))
			.orElseThrow();
		assertEquals(SpeechProcessingStatus.FAILED, failed.getProcessingStatus());
		assertEquals(
			ErrorCode.STT_PROCESSING_FAILED.getMessage(),
			failed.getFailureMessage()
		);
	}

	@Test
	void STT_rate_거절은_Google을_호출하지_않고_DB_FAILED와_permit을_복구한다() {
		SpeechAnswerAsyncProperties properties =
			new SpeechAnswerAsyncProperties(
				Duration.ofSeconds(20),
				MAX_CONCURRENT_REQUESTS,
				Duration.ofSeconds(60),
				Duration.ofSeconds(20)
			);
		SpeechTranscriptionConcurrencyPolicy concurrencyPolicy =
			new SpeechTranscriptionConcurrencyPolicy(
				properties.maxConcurrentRequests()
			);
		SpeechShutdownPolicy shutdownPolicy = new SpeechShutdownPolicy(
			properties.shutdownDrainTimeout()
		);
		InFlightSpeechAnswerRegistry inFlightRegistry =
			new InFlightSpeechAnswerRegistry();
		SpeechAnswerCoordinator rateLimitedCoordinator =
			new SpeechAnswerCoordinator(
			stateService,
			transcriber,
			Runnable::run,
			concurrencyPolicy,
			new SpeechTranscriptionRateLimiter(
				new GoogleSpeechRateLimitProperties(1),
				ProviderRateLimitMetricsRecorder.NOOP,
				TimeMeter.SYSTEM_NANOTIME
			),
			inFlightRegistry,
			new SpeechAnswerLifecycle(
				inFlightRegistry,
				concurrencyPolicy,
				shutdownPolicy
			)
		);
		LearningSession session = saveSession();
		Long sessionQuestionId = session.getCurrentQuestion().getId();
		SpeechAnswerTask first = rateLimitedCoordinator.uploadAsync(
			speechRequest(session, sessionQuestionId, requestKey("rate-first"))
		);
		assertTrue(transcriber.complete(0));
		await(first);

		String rejectedKey = requestKey("rate-rejected");
		SpeechAnswerTask rejected = rateLimitedCoordinator.uploadAsync(
			speechRequest(session, sessionQuestionId, rejectedKey)
		);

		ApiException failure = assertThrows(
			ApiException.class,
			() -> await(rejected)
		);
		assertEquals(ErrorCode.AI_REQUEST_LIMIT_EXCEEDED,
			failure.getErrorCode());
		assertEquals(HttpStatus.TOO_MANY_REQUESTS,
			failure.getErrorCode().getHttpStatus());
		assertEquals(1, transcriber.callCount());
		assertEquals(0, concurrencyPolicy.activeRequests());
		SpeechAnswer failed = findByRequestKey(speechAnswerRepository, rejectedKey)
			.orElseThrow();
		assertEquals(SpeechProcessingStatus.FAILED,
			failed.getProcessingStatus());
		assertEquals(ErrorCode.AI_REQUEST_LIMIT_EXCEEDED.getMessage(),
			failed.getFailureMessage());
	}

	@Test
	void 마지막_구독_취소_중에는_새_구독이_공유_STT에_합류하지_않는다()
		throws Exception {
		LearningSession session = saveSession();
		Long sessionQuestionId = session.getCurrentQuestion().getId();
		String requestSuffix = "cancel-rejoin-race";
		transcriber.blockCancellation();
		SpeechAnswerTask first = upload(
			session,
			sessionQuestionId,
			requestSuffix
		);
		CompletableFuture<Boolean> cancellation =
			CompletableFuture.supplyAsync(first::cancel);
		assertTrue(transcriber.awaitCancellationStarted());

		CountDownLatch rejoinStarted = new CountDownLatch(1);
		CompletableFuture<SpeechAnswerTask> rejoin =
			CompletableFuture.supplyAsync(() -> {
				rejoinStarted.countDown();
				return upload(
					session,
					sessionQuestionId,
					requestSuffix
				);
			});
		assertTrue(rejoinStarted.await(2, TimeUnit.SECONDS));
		try {
			assertThrows(
				TimeoutException.class,
				() -> rejoin.get(200, TimeUnit.MILLISECONDS)
			);
		} finally {
			transcriber.releaseCancellation();
		}

		assertTrue(cancellation.get(2, TimeUnit.SECONDS));
		SpeechAnswerTask rejoined = rejoin.get(2, TimeUnit.SECONDS);
		ApiException timeout = assertThrows(
			ApiException.class,
			() -> await(rejoined)
		);
		assertEquals(ErrorCode.STT_PROCESSING_TIMEOUT, timeout.getErrorCode());
		assertEquals(1, transcriber.callCount());
		assertEquals(1, transcriber.cancellationCount());
		assertEquals(
			SpeechProcessingStatus.FAILED,
			findByRequestKey(speechAnswerRepository, requestKey(requestSuffix))
				.orElseThrow()
				.getProcessingStatus()
		);
	}

	@Test
	void lease_회수와_provider_완료가_경쟁하면_완료_결과를_보존한다()
		throws Exception {
		LearningSession session = saveSession();
		Long sessionQuestionId = session.getCurrentQuestion().getId();
		String requestKey = requestKey("lease-complete-race");
		SpeechAnswerStartResult processing = stateService.start(LearningJpaTestFixture.USER_ID,
			session.getId(),
			sessionQuestionId,
			requestKey
		);
		jdbcTemplate.update(
			"update speech_answers set lease_expires_at = ? where id = ?",
			Timestamp.from(Instant.now().minusSeconds(1)),
			processing.speechAnswer().getId()
		);

		CountDownLatch completionFlushed = new CountDownLatch(1);
		CountDownLatch allowCompletionCommit = new CountDownLatch(1);
		TransactionTemplate transaction = new TransactionTemplate(
			transactionManager
		);
		CompletableFuture<Void> completion = CompletableFuture.runAsync(() ->
			transaction.executeWithoutResult(status -> {
				assertTrue(stateService.complete(
					processing.speechAnswer().getId(),
					processing.processingToken(),
					"He is running.",
					0.94,
					"TEST_STT"
				).isPresent());
				speechAnswerRepository.flush();
				completionFlushed.countDown();
				await(allowCompletionCommit);
			})
		);
		assertTrue(completionFlushed.await(2, TimeUnit.SECONDS));

		CountDownLatch reclaimStarted = new CountDownLatch(1);
		CompletableFuture<SpeechAnswerStartResult> reclaim =
			CompletableFuture.supplyAsync(() -> {
				reclaimStarted.countDown();
				return stateService.start(LearningJpaTestFixture.USER_ID,
					session.getId(),
					sessionQuestionId,
					requestKey
				);
			});
		assertTrue(reclaimStarted.await(2, TimeUnit.SECONDS));
		try {
			assertThrows(
				TimeoutException.class,
				() -> reclaim.get(200, TimeUnit.MILLISECONDS)
			);
		} finally {
			allowCompletionCommit.countDown();
		}

		completion.get(2, TimeUnit.SECONDS);
		SpeechAnswerStartResult resolved = reclaim.get(2, TimeUnit.SECONDS);
		SpeechAnswer completed = findByRequestKey(speechAnswerRepository, requestKey)
			.orElseThrow();
		assertTrue(resolved.isCompleted());
		assertEquals(
			SpeechProcessingStatus.COMPLETED,
			completed.getProcessingStatus()
		);
		assertEquals("He is running.", completed.getTranscript());
		assertEquals(0.94, completed.getConfidence());
		assertEquals("TEST_STT", completed.getSttProvider());
	}

	@Test
	void 종료는_진행_STT를_drain하고_신규_요청을_거절한다()
		throws Exception {
		SpeechServiceFixture draining = newService(
			Duration.ofSeconds(1)
		);
		SpeechAnswerCoordinator drainingCoordinator = draining.coordinator();
		LearningSession session = saveSession();
		Long sessionQuestionId = session.getCurrentQuestion().getId();
		SpeechAnswerTask active = drainingCoordinator.uploadAsync(
			speechRequest(session, sessionQuestionId, requestKey("shutdown-active"))
		);
		CountDownLatch shutdown = new CountDownLatch(1);

		draining.lifecycle().stop(shutdown::countDown);

		assertEquals(1L, shutdown.getCount());
		ApiException rejected = assertThrows(
			ApiException.class,
			() -> drainingCoordinator.uploadAsync(
				speechRequest(
					session,
					sessionQuestionId,
					requestKey("shutdown-rejected")
				)
			)
		);
		assertEquals(
			ErrorCode.STT_PROCESSING_OVERLOADED,
			rejected.getErrorCode()
		);
		assertEquals(1, transcriber.callCount());

		assertTrue(transcriber.complete(0));
		assertEquals("He is running.", await(active).transcript());
		assertTrue(shutdown.await(1, TimeUnit.SECONDS));
		assertEquals(
			SpeechProcessingStatus.COMPLETED,
			findByRequestKey(speechAnswerRepository, requestKey("shutdown-active"))
				.orElseThrow()
				.getProcessingStatus()
		);
	}

	@Test
	void 종료_drain_제한시간을_넘기면_STT를_취소하고_FAILED로_저장한다()
		throws Exception {
		SpeechServiceFixture draining = newService(
			Duration.ofMillis(50)
		);
		SpeechAnswerCoordinator drainingCoordinator = draining.coordinator();
		LearningSession session = saveSession();
		Long sessionQuestionId = session.getCurrentQuestion().getId();
		String requestKey = requestKey("shutdown-timeout");
		SpeechAnswerTask active = drainingCoordinator.uploadAsync(
			speechRequest(session, sessionQuestionId, requestKey)
		);
		CountDownLatch shutdown = new CountDownLatch(1);

		draining.lifecycle().stop(shutdown::countDown);

		assertTrue(shutdown.await(2, TimeUnit.SECONDS));
		ApiException timeout = assertThrows(
			ApiException.class,
			() -> await(active)
		);
		assertEquals(
			ErrorCode.STT_PROCESSING_TIMEOUT,
			timeout.getErrorCode()
		);
		assertEquals(1, transcriber.cancellationCount());
		SpeechAnswer failed = findByRequestKey(speechAnswerRepository, requestKey)
			.orElseThrow();
		assertEquals(
			SpeechProcessingStatus.FAILED,
			failed.getProcessingStatus()
		);
		assertEquals(
			ErrorCode.STT_PROCESSING_TIMEOUT.getMessage(),
			failed.getFailureMessage()
		);
	}

	private SpeechServiceFixture newService(Duration shutdownDrainTimeout) {
		SpeechAnswerAsyncProperties properties =
			new SpeechAnswerAsyncProperties(
				Duration.ofSeconds(20),
				MAX_CONCURRENT_REQUESTS,
				Duration.ofSeconds(60),
				shutdownDrainTimeout
			);
		SpeechTranscriptionConcurrencyPolicy concurrencyPolicy =
			new SpeechTranscriptionConcurrencyPolicy(
				properties.maxConcurrentRequests()
			);
		SpeechShutdownPolicy shutdownPolicy = new SpeechShutdownPolicy(
			properties.shutdownDrainTimeout()
		);
		InFlightSpeechAnswerRegistry inFlightRegistry =
			new InFlightSpeechAnswerRegistry();
		SpeechAnswerLifecycle lifecycle = new SpeechAnswerLifecycle(
			inFlightRegistry,
			concurrencyPolicy,
			shutdownPolicy
		);
		SpeechAnswerCoordinator coordinator = new SpeechAnswerCoordinator(
			stateService,
			transcriber,
			Runnable::run,
			concurrencyPolicy,
			SpeechTranscriptionRateLimit.UNLIMITED,
			inFlightRegistry,
			lifecycle
		);
		return new SpeechServiceFixture(coordinator, lifecycle);
	}

	private record SpeechServiceFixture(
		SpeechAnswerCoordinator coordinator,
		SpeechAnswerLifecycle lifecycle
	) {
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
		return speechAnswerCoordinator.uploadAsync(
			speechRequest(session, sessionQuestionId, requestKey(requestSuffix))
		);
	}

	private SpeechAnswerRequest speechRequest(
		LearningSession session,
		Long sessionQuestionId,
		String requestKey
	) {
		return new SpeechAnswerRequest(
			LearningJpaTestFixture.USER_ID,
			session.getId(),
			sessionQuestionId,
			requestKey,
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

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(2, TimeUnit.SECONDS)) {
				throw new IllegalStateException("동시성 테스트 대기 시간이 초과되었습니다.");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SpeechTestConfiguration {

		@Bean
		SpeechAnswerAsyncProperties speechAnswerAsyncProperties() {
			return new SpeechAnswerAsyncProperties(
				Duration.ofSeconds(20),
				MAX_CONCURRENT_REQUESTS,
				Duration.ofSeconds(60),
				Duration.ofSeconds(20)
			);
		}

		@Bean
		SpeechTranscriptionConcurrencyPolicy concurrencyPolicy(
			SpeechAnswerAsyncProperties properties
		) {
			return new SpeechTranscriptionConcurrencyPolicy(
				properties.maxConcurrentRequests()
			);
		}

		@Bean
		SpeechProcessingLease processingLease(
			SpeechAnswerAsyncProperties properties
		) {
			return new SpeechProcessingLease(properties.processingLease());
		}

		@Bean
		SpeechShutdownPolicy shutdownPolicy(
			SpeechAnswerAsyncProperties properties
		) {
			return new SpeechShutdownPolicy(
				properties.shutdownDrainTimeout()
			);
		}

		@Bean
		SpeechTranscriptionRateLimiter rateLimiter() {
			return new SpeechTranscriptionRateLimiter(
				new GoogleSpeechRateLimitProperties(240),
				ProviderRateLimitMetricsRecorder.NOOP,
				TimeMeter.SYSTEM_NANOTIME
			);
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
		private final AtomicInteger cancellations = new AtomicInteger();
		private volatile CountDownLatch cancellationStarted =
			new CountDownLatch(0);
		private volatile CountDownLatch allowCancellation =
			new CountDownLatch(0);

		void reset() {
			releaseCancellation();
			requests.clear();
			cancellations.set(0);
			cancellationStarted = new CountDownLatch(0);
			allowCancellation = new CountDownLatch(0);
		}

		int callCount() {
			return requests.size();
		}

		int cancellationCount() {
			return cancellations.get();
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

		void blockCancellation() {
			cancellationStarted = new CountDownLatch(1);
			allowCancellation = new CountDownLatch(1);
		}

		boolean awaitCancellationStarted() throws InterruptedException {
			return cancellationStarted.await(2, TimeUnit.SECONDS);
		}

		void releaseCancellation() {
			allowCancellation.countDown();
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
				() -> {
					CountDownLatch started = cancellationStarted;
					CountDownLatch cancellationAllowed = allowCancellation;
					cancellations.incrementAndGet();
					started.countDown();
					await(cancellationAllowed);
					return result.cancel(true);
				}
			);
		}

		private void await(CountDownLatch latch) {
			try {
				if (!latch.await(2, TimeUnit.SECONDS)) {
					throw new IllegalStateException(
						"STT 취소 허용 대기 시간이 초과되었습니다."
					);
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
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
