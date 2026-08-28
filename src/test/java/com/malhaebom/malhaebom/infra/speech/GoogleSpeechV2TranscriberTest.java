package com.malhaebom.malhaebom.infra.speech;

import com.google.api.core.SettableApiFuture;
import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.cloud.speech.v2.*;
import com.google.protobuf.ByteString;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionTask;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleSpeechV2TranscriberTest {
	private static final byte[] AUDIO_CONTENT = {1, 2, 3};
	private static final SpeechAudio AUDIO = new SpeechAudio(
		AUDIO_CONTENT,
		"audio/webm;codecs=opus"
	);

	@Mock
	private SpeechClient client;

	@Mock
	private UnaryCallable<RecognizeRequest, RecognizeResponse> recognizeCallable;

	private GoogleSpeechV2Transcriber transcriber;

	@BeforeEach
	void setUp() {
		when(client.recognizeCallable()).thenReturn(recognizeCallable);
		GoogleSpeechV2Properties properties = new GoogleSpeechV2Properties(
			"en-US",
			Duration.ofSeconds(15),
			"global",
			"_",
			"short",
			5.0f
		);
		transcriber = new GoogleSpeechV2Transcriber(
			client,
			properties,
			"malhaebom-504606"
		);
	}

	@Test
	void Google_future가_완료되기_전에는_음성_변환_작업도_완료되지_않는다()
		throws Exception {
		SettableApiFuture<RecognizeResponse> googleFuture = pendingFuture();

		SpeechTranscriptionTask task = transcriber.transcribeAsync(
			AUDIO,
			List.of("He is running.", "He's running.")
		);

		assertThat(task.result().toCompletableFuture()).isNotDone();

		googleFuture.set(response(
			alternative(" He is ", 0.94f),
			alternative(" running. ", 0.86f)
		));

		SpeechTranscriptionResult result = task.result()
			.toCompletableFuture()
			.get(1, TimeUnit.SECONDS);
		assertThat(result.transcript()).isEqualTo("He is running.");
		assertThat(result.confidence()).isEqualTo(0.9);
		assertThat(result.provider()).isEqualTo("GOOGLE_CLOUD_STT_V2");

		ArgumentCaptor<RecognizeRequest> requestCaptor =
			ArgumentCaptor.forClass(RecognizeRequest.class);
		verify(recognizeCallable).futureCall(requestCaptor.capture());
		RecognizeRequest request = requestCaptor.getValue();
		assertThat(request.getRecognizer()).isEqualTo(
			"projects/malhaebom-504606/locations/global/recognizers/_"
		);
		assertThat(request.getContent()).isEqualTo(
			ByteString.copyFrom(AUDIO_CONTENT)
		);
		assertThat(request.getConfig().hasAutoDecodingConfig()).isTrue();
		assertThat(request.getConfig().getLanguageCodesList())
			.containsExactly("en-US");
		assertThat(request.getConfig().getModel()).isEqualTo("short");
		assertThat(
			request.getConfig()
				.getAdaptation()
				.getPhraseSets(0)
				.getInlinePhraseSet()
				.getPhrasesList()
		)
			.extracting(
				com.google.cloud.speech.v2.PhraseSet.Phrase::getValue,
				com.google.cloud.speech.v2.PhraseSet.Phrase::getBoost
			)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple("He is running.", 5.0f),
				org.assertj.core.groups.Tuple.tuple("He's running.", 5.0f)
			);
		assertThat(transcriber.provider()).isEqualTo(
			"GOOGLE_CLOUD_STT_V2"
		);
	}

	@Test
	void 작업_취소는_진행_중인_Google_future에_전파된다() {
		SettableApiFuture<RecognizeResponse> googleFuture = pendingFuture();
		SpeechTranscriptionTask task = transcriber.transcribeAsync(
			AUDIO,
			List.of()
		);

		assertThat(task.cancel()).isTrue();

		assertThat(googleFuture.isCancelled()).isTrue();
		assertThat(task.result().toCompletableFuture()).isCancelled();
	}

	@Test
	void confidence가_제공되지_않으면_null을_반환한다() {
		completedFuture(response(alternative("Hello.", 0.0f)));

		SpeechTranscriptionResult result = transcribe(
			AUDIO,
			List.of()
		);

		assertThat(result.transcript()).isEqualTo("Hello.");
		assertThat(result.confidence()).isNull();
	}

	@Test
	void 유효한_적응_문구가_없으면_adaptation을_설정하지_않는다() {
		completedFuture(response(alternative("Hello.", 0.9f)));

		transcribe(AUDIO, List.of());

		ArgumentCaptor<RecognizeRequest> requestCaptor =
			ArgumentCaptor.forClass(RecognizeRequest.class);
		verify(recognizeCallable).futureCall(requestCaptor.capture());
		assertThat(requestCaptor.getValue().getConfig().hasAdaptation())
			.isFalse();
	}

	@Test
	void 잘못된_문구와_중복을_제외하고_적응_문구를_구성한다() {
		completedFuture(response(alternative("Hello.", 0.9f)));

		transcribe(
			AUDIO,
			java.util.Arrays.asList(
				"  Hello world  ",
				"Hello world",
				" ",
				null,
				"a".repeat(101)
			)
		);

		ArgumentCaptor<RecognizeRequest> requestCaptor =
			ArgumentCaptor.forClass(RecognizeRequest.class);
		verify(recognizeCallable).futureCall(requestCaptor.capture());
		assertThat(
			requestCaptor.getValue()
				.getConfig()
				.getAdaptation()
				.getPhraseSets(0)
				.getInlinePhraseSet()
				.getPhrasesList()
		)
			.extracting(com.google.cloud.speech.v2.PhraseSet.Phrase::getValue)
			.containsExactly("Hello world");
	}

	@Test
	void 적응_문구는_PhraseSet_한도까지만_사용한다() {
		completedFuture(response(alternative("Hello.", 0.9f)));
		List<String> phrases = java.util.stream.IntStream.range(0, 1_201)
			.mapToObj(index -> "phrase " + index)
			.toList();

		transcribe(AUDIO, phrases);

		ArgumentCaptor<RecognizeRequest> requestCaptor =
			ArgumentCaptor.forClass(RecognizeRequest.class);
		verify(recognizeCallable).futureCall(requestCaptor.capture());
		assertThat(
			requestCaptor.getValue()
				.getConfig()
				.getAdaptation()
				.getPhraseSets(0)
				.getInlinePhraseSet()
				.getPhrasesCount()
		).isEqualTo(1_200);
	}

	@Test
	void 인식된_transcript가_없으면_인식_실패로_변환한다() {
		completedFuture(RecognizeResponse.getDefaultInstance());

		assertApiException(
			ErrorCode.SPEECH_NOT_RECOGNIZED,
			() -> transcribe(AUDIO, List.of())
		);
	}

	@Test
	void RESOURCE_EXHAUSTED를_요청_제한_예외로_변환한다() {
		failedFuture(googleException(Status.Code.RESOURCE_EXHAUSTED));

		assertApiException(
			ErrorCode.AI_REQUEST_LIMIT_EXCEEDED,
			() -> transcribe(AUDIO, List.of())
		);
	}

	@Test
	void DEADLINE_EXCEEDED를_타임아웃_예외로_변환한다() {
		failedFuture(googleException(Status.Code.DEADLINE_EXCEEDED));

		assertApiException(
			ErrorCode.STT_PROCESSING_TIMEOUT,
			() -> transcribe(AUDIO, List.of())
		);
	}

	@Test
	void 인증과_그_밖의_Google_오류를_안전한_처리_실패로_변환한다() {
		failedFuture(googleException(Status.Code.UNAUTHENTICATED));

		assertApiException(
			ErrorCode.STT_PROCESSING_FAILED,
			() -> transcribe(AUDIO, List.of())
		);
	}

	@Test
	void 예상하지_않은_동기_오류는_STT_처리_실패로_감추지_않는다() {
		IllegalStateException failure = new IllegalStateException(
			"invalid client state"
		);
		when(recognizeCallable.futureCall(any(RecognizeRequest.class)))
			.thenThrow(failure);

		assertThatThrownBy(
			() -> transcriber.transcribeAsync(AUDIO, List.of())
		).isSameAs(failure);
	}

	private SpeechTranscriptionResult transcribe(
		SpeechAudio audio,
		List<String> adaptationPhrases
	) {
		try {
			return transcriber.transcribeAsync(audio, adaptationPhrases)
				.result()
				.toCompletableFuture()
				.join();
		} catch (CompletionException exception) {
			if (exception.getCause() instanceof RuntimeException cause) {
				throw cause;
			}
			throw exception;
		}
	}

	private SettableApiFuture<RecognizeResponse> pendingFuture() {
		SettableApiFuture<RecognizeResponse> future =
			SettableApiFuture.create();
		when(recognizeCallable.futureCall(any(RecognizeRequest.class)))
			.thenReturn(future);
		return future;
	}

	private void completedFuture(RecognizeResponse response) {
		SettableApiFuture<RecognizeResponse> future = pendingFuture();
		future.set(response);
	}

	private void failedFuture(Throwable exception) {
		SettableApiFuture<RecognizeResponse> future = pendingFuture();
		future.setException(exception);
	}

	private RecognizeResponse response(
		SpeechRecognitionAlternative... alternatives
	) {
		RecognizeResponse.Builder response = RecognizeResponse.newBuilder();
		for (SpeechRecognitionAlternative alternative : alternatives) {
			response.addResults(
				SpeechRecognitionResult.newBuilder()
					.addAlternatives(alternative)
			);
		}
		return response.build();
	}

	private SpeechRecognitionAlternative alternative(String transcript, float confidence) {
		return SpeechRecognitionAlternative.newBuilder()
			.setTranscript(transcript)
			.setConfidence(confidence)
			.build();
	}

	private com.google.api.gax.rpc.ApiException googleException(Status.Code code) {
		return ApiExceptionFactory.createException(
			new RuntimeException("Google API error"),
			GrpcStatusCode.of(code),
			false
		);
	}
}
