package com.malhaebom.malhaebom.infra.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.cloud.speech.v2.RecognizeRequest;
import com.google.cloud.speech.v2.RecognizeResponse;
import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechRecognitionAlternative;
import com.google.cloud.speech.v2.SpeechRecognitionResult;
import com.google.protobuf.ByteString;
import com.malhaebom.malhaebom.global.exception.AiRequestLimitExceededException;
import com.malhaebom.malhaebom.global.exception.SpeechNotRecognizedException;
import com.malhaebom.malhaebom.global.exception.SpeechProcessingFailedException;
import com.malhaebom.malhaebom.global.exception.SpeechTranscriptionTimeoutException;
import com.malhaebom.malhaebom.infra.gcp.GoogleCloudProperties;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;

import io.grpc.Status;

@ExtendWith(MockitoExtension.class)
class GoogleSpeechV2TranscriberTest {

	private static final byte[] AUDIO_CONTENT = {1, 2, 3};
	private static final SpeechAudio AUDIO = new SpeechAudio(
		AUDIO_CONTENT,
		"audio/webm;codecs=opus"
	);

	@Mock
	private SpeechClient client;

	private GoogleSpeechV2Transcriber transcriber;

	@BeforeEach
	void setUp() {
		GoogleSpeechV2Properties properties = new GoogleSpeechV2Properties(
			"en-US",
			Duration.ofSeconds(15),
			"global",
			"_",
			"short"
		);
		GoogleCloudProperties cloudProperties = new GoogleCloudProperties(
			"malhaebom-504606",
			null
		);
		transcriber = new GoogleSpeechV2Transcriber(
			client,
			properties,
			cloudProperties
		);
	}

	@Test
	void V2_Recognize_요청에_암시적_Recognizer와_음성_바이트를_담는다() {
		when(client.recognize(any(RecognizeRequest.class)))
			.thenReturn(response(
				alternative("He is", 0.94f),
				alternative("running.", 0.86f)
			));

		SpeechTranscriptionResult result = transcriber.transcribe(AUDIO);

		ArgumentCaptor<RecognizeRequest> requestCaptor =
			ArgumentCaptor.forClass(RecognizeRequest.class);
		verify(client).recognize(requestCaptor.capture());
		RecognizeRequest request = requestCaptor.getValue();
		assertThat(request.getRecognizer()).isEqualTo(
			"projects/malhaebom-504606/locations/global/recognizers/_"
		);
		assertThat(request.getContent()).isEqualTo(
			ByteString.copyFrom(AUDIO_CONTENT)
		);
		assertThat(request.hasConfig()).isTrue();
		assertThat(request.getConfig().hasAutoDecodingConfig()).isTrue();
		assertThat(request.getConfig().getLanguageCodesList())
			.containsExactly("en-US");
		assertThat(request.getConfig().getModel()).isEqualTo("short");
		assertThat(result.transcript()).isEqualTo("He is running.");
		assertThat(result.confidence()).isEqualTo(0.9);
		assertThat(result.provider()).isEqualTo("GOOGLE_CLOUD_STT_V2");
		assertThat(transcriber.provider()).isEqualTo(
			"GOOGLE_CLOUD_STT_V2"
		);
	}

	@Test
	void confidence가_제공되지_않으면_null을_반환한다() {
		when(client.recognize(any(RecognizeRequest.class)))
			.thenReturn(response(alternative("Hello.", 0.0f)));

		SpeechTranscriptionResult result = transcriber.transcribe(AUDIO);

		assertThat(result.transcript()).isEqualTo("Hello.");
		assertThat(result.confidence()).isNull();
	}

	@Test
	void 인식된_transcript가_없으면_인식_실패로_변환한다() {
		when(client.recognize(any(RecognizeRequest.class)))
			.thenReturn(RecognizeResponse.getDefaultInstance());

		assertThrows(
			SpeechNotRecognizedException.class,
			() -> transcriber.transcribe(AUDIO)
		);
	}

	@Test
	void RESOURCE_EXHAUSTED를_요청_제한_예외로_변환한다() {
		ApiException googleException = googleException(
			Status.Code.RESOURCE_EXHAUSTED
		);
		when(client.recognize(any(RecognizeRequest.class)))
			.thenThrow(googleException);

		AiRequestLimitExceededException thrown = assertThrows(
			AiRequestLimitExceededException.class,
			() -> transcriber.transcribe(AUDIO)
		);

		assertSame(googleException, thrown.getCause());
	}

	@Test
	void DEADLINE_EXCEEDED를_타임아웃_예외로_변환한다() {
		ApiException googleException = googleException(
			Status.Code.DEADLINE_EXCEEDED
		);
		when(client.recognize(any(RecognizeRequest.class)))
			.thenThrow(googleException);

		SpeechTranscriptionTimeoutException thrown = assertThrows(
			SpeechTranscriptionTimeoutException.class,
			() -> transcriber.transcribe(AUDIO)
		);

		assertSame(googleException, thrown.getCause());
	}

	@Test
	void 인증과_그_밖의_Google_오류를_안전한_처리_실패로_변환한다() {
		ApiException googleException = googleException(
			Status.Code.UNAUTHENTICATED
		);
		when(client.recognize(any(RecognizeRequest.class)))
			.thenThrow(googleException);

		SpeechProcessingFailedException thrown = assertThrows(
			SpeechProcessingFailedException.class,
			() -> transcriber.transcribe(AUDIO)
		);

		assertSame(googleException, thrown.getCause());
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

	private SpeechRecognitionAlternative alternative(
		String transcript,
		float confidence
	) {
		return SpeechRecognitionAlternative.newBuilder()
			.setTranscript(transcript)
			.setConfidence(confidence)
			.build();
	}

	private ApiException googleException(Status.Code code) {
		return ApiExceptionFactory.createException(
			new RuntimeException("Google API error"),
			GrpcStatusCode.of(code),
			false
		);
	}
}
