package com.malhaebom.malhaebom.integration.learning;

import static com.malhaebom.malhaebom.support.SpeechAnswerTestQueries.findByRequestKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.sql.DataSource;

import io.github.bucket4j.TimeMeter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.zaxxer.hikari.HikariDataSource;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.SpeechProcessingStatus;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.infra.async.AsyncConfiguration;
import com.malhaebom.malhaebom.infra.async.SpeechAnswerAsyncProperties;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.infra.observability.ProviderRateLimitMetricsRecorder;
import com.malhaebom.malhaebom.infra.speech.GoogleSpeechRateLimitProperties;
import com.malhaebom.malhaebom.service.policy.SpeechTranscriptionConcurrencyPolicy;
import com.malhaebom.malhaebom.infra.speech.SpeechTranscriptionRateLimiter;
import com.malhaebom.malhaebom.service.speech.InFlightSpeechAnswerRegistry;
import com.malhaebom.malhaebom.service.speech.SpeechAnswerLifecycle;
import com.malhaebom.malhaebom.service.speech.SpeechAnswerCoordinator;
import com.malhaebom.malhaebom.service.speech.SpeechAnswerStateService;
import com.malhaebom.malhaebom.service.ChildProfileService;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerResult;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerTask;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerRequest;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionTask;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;
import com.malhaebom.malhaebom.service.policy.SpeechProcessingLease;
import com.malhaebom.malhaebom.service.policy.SpeechShutdownPolicy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
	"spring.datasource.hikari.maximum-pool-size=1",
	"spring.datasource.hikari.minimum-idle=1",
	"spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
	JpaAuditingConfiguration.class,
	SpeechAnswerStateService.class,
	SpeechAnswerCoordinator.class,
	InFlightSpeechAnswerRegistry.class,
	SpeechAnswerLifecycle.class,
	SpeechAnswerTransactionBoundaryJpaTest.SpeechTestConfiguration.class
})
class SpeechAnswerTransactionBoundaryJpaTest {
	private static final String REQUEST_KEY = "speech-transaction-boundary-request";
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
	private TestSpeechTranscriber transcriber;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private DataSource dataSource;
	@MockitoBean
	private ChildProfileService childProfileService;

	@BeforeEach
	void setUp() {
		transcriber.reset();
	}

	@Test
	void STT_future를_기다리는_동안_DB_커넥션과_트랜잭션을_점유하지_않는다() {
		HikariDataSource hikari = assertInstanceOf(
			HikariDataSource.class,
			dataSource
		);
		assertEquals(1, hikari.getMaximumPoolSize());
		LearningSession session = LearningJpaTestFixture.saveSession(
			questionRepository,
			learningSessionRepository,
			"He is ____ing."
		);
		Long sessionQuestionId = session.getCurrentQuestion().getId();

		SpeechAnswerTask task = speechAnswerCoordinator.uploadAsync(
			new SpeechAnswerRequest(
				LearningJpaTestFixture.USER_ID,
				session.getId(),
				sessionQuestionId,
				REQUEST_KEY,
				AUDIO
			)
		);

		assertFalse(task.result().toCompletableFuture().isDone());
		assertEquals(List.of(false), transcriber.transactionStates);
		SpeechAnswer processing = findByRequestKey(speechAnswerRepository, REQUEST_KEY)
			.orElseThrow();
		assertEquals(
			SpeechProcessingStatus.PROCESSING,
			processing.getProcessingStatus()
		);

		TransactionTemplate transaction = new TransactionTemplate(
			transactionManager
		);
		assertTrue(Boolean.TRUE.equals(transaction.execute(status ->
			learningSessionRepository.existsById(session.getId())
		)));

		assertTrue(transcriber.complete());
		SpeechAnswerResult result = task.result().toCompletableFuture().join();
		assertEquals("He is running.", result.transcript());
		assertEquals(
			SpeechProcessingStatus.COMPLETED,
			speechAnswerRepository.findById(processing.getId())
				.orElseThrow()
				.getProcessingStatus()
		);
		assertFalse(TransactionSynchronizationManager
			.isActualTransactionActive());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SpeechTestConfiguration {

		@Bean
		TestSpeechTranscriber speechTranscriber() {
			return new TestSpeechTranscriber();
		}

		@Bean(name = AsyncConfiguration.SPEECH_COMPLETION_EXECUTOR)
		Executor speechCompletionExecutor() {
			return Runnable::run;
		}

		@Bean
		SpeechAnswerAsyncProperties speechAnswerAsyncProperties() {
			return new SpeechAnswerAsyncProperties(
				Duration.ofSeconds(20),
				8,
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
	}

	private static final class TestSpeechTranscriber implements SpeechTranscriber {

		private final List<Boolean> transactionStates = new ArrayList<>();
		private CompletableFuture<SpeechTranscriptionResult> result;

		void reset() {
			transactionStates.clear();
			result = new CompletableFuture<>();
		}

		boolean complete() {
			return result.complete(new SpeechTranscriptionResult(
				"He is running.",
				0.94,
				"TEST_STT"
			));
		}

		@Override
		public String provider() {
			return "TEST_STT";
		}

		@Override
		public SpeechTranscriptionTask transcribeAsync(SpeechAudio audio, List<String> adaptationPhrases) {
			transactionStates.add(TransactionSynchronizationManager
				.isActualTransactionActive());
			return new SpeechTranscriptionTask(
				result,
				() -> result.cancel(true)
			);
		}
	}
}
