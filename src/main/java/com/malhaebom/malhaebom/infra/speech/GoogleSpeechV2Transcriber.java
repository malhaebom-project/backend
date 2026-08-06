package com.malhaebom.malhaebom.infra.speech;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.speech.v2.AutoDetectDecodingConfig;
import com.google.cloud.speech.v2.RecognitionConfig;
import com.google.cloud.speech.v2.RecognizeRequest;
import com.google.cloud.speech.v2.RecognizeResponse;
import com.google.cloud.speech.v2.RecognizerName;
import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechRecognitionAlternative;
import com.google.protobuf.ByteString;
import com.malhaebom.malhaebom.global.exception.AiRequestLimitExceededException;
import com.malhaebom.malhaebom.global.exception.SpeechNotRecognizedException;
import com.malhaebom.malhaebom.global.exception.SpeechProcessingFailedException;
import com.malhaebom.malhaebom.global.exception.SpeechTranscriptionTimeoutException;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

public class GoogleSpeechV2Transcriber implements SpeechTranscriber {

	public static final String PROVIDER = "GOOGLE_CLOUD_STT_V2";

	private static final int CONFIDENCE_SCALE = 4;

	private final SpeechClient client;
	private final RecognitionConfig recognitionConfig;
	private final String recognizer;

	public GoogleSpeechV2Transcriber(
		SpeechClient client,
		GoogleSpeechV2Properties properties
	) {
		this.client = client;
		this.recognitionConfig = createRecognitionConfig(properties);
		this.recognizer = createRecognizerName(properties);
	}

	@Override
	public String provider() {
		return PROVIDER;
	}

	@Override
	public SpeechTranscriptionResult transcribe(SpeechAudio audio) {
		RecognizeRequest request = RecognizeRequest.newBuilder()
			.setRecognizer(recognizer)
			.setConfig(recognitionConfig)
			.setContent(ByteString.copyFrom(audio.content()))
			.build();

		RecognizeResponse response;
		try {
			response = client.recognize(request);
		} catch (ApiException exception) {
			throw mapGoogleException(exception);
		} catch (RuntimeException exception) {
			throw new SpeechProcessingFailedException(exception);
		}

		return normalize(response);
	}

	private SpeechTranscriptionResult normalize(RecognizeResponse response) {
		if (response == null) {
			throw new SpeechProcessingFailedException();
		}

		List<SpeechRecognitionAlternative> alternatives = response
			.getResultsList()
			.stream()
			.filter(result -> result.getAlternativesCount() > 0)
			.map(result -> result.getAlternatives(0))
			.toList();
		String transcript = alternatives.stream()
			.map(SpeechRecognitionAlternative::getTranscript)
			.map(String::strip)
			.filter(part -> !part.isBlank())
			.reduce((first, second) -> first + " " + second)
			.orElse("");

		if (transcript.isBlank()) {
			throw new SpeechNotRecognizedException();
		}

		return new SpeechTranscriptionResult(
			transcript,
			normalizeConfidence(alternatives),
			provider()
		);
	}

	private Double normalizeConfidence(
		List<SpeechRecognitionAlternative> alternatives
	) {
		double average = alternatives.stream()
			.mapToDouble(SpeechRecognitionAlternative::getConfidence)
			.filter(confidence -> confidence > 0.0 && confidence <= 1.0)
			.average()
			.orElse(Double.NaN);
		if (Double.isNaN(average)) {
			return null;
		}

		return BigDecimal.valueOf(average)
			.setScale(CONFIDENCE_SCALE, RoundingMode.HALF_UP)
			.doubleValue();
	}

	private RuntimeException mapGoogleException(ApiException exception) {
		StatusCode statusCode = exception.getStatusCode();
		StatusCode.Code code = statusCode == null
			? null
			: statusCode.getCode();

		if (code == StatusCode.Code.RESOURCE_EXHAUSTED) {
			return new AiRequestLimitExceededException(exception);
		}
		if (code == StatusCode.Code.DEADLINE_EXCEEDED) {
			return new SpeechTranscriptionTimeoutException(exception);
		}
		return new SpeechProcessingFailedException(exception);
	}

	private static RecognitionConfig createRecognitionConfig(
		GoogleSpeechV2Properties properties
	) {
		return RecognitionConfig.newBuilder()
			.setAutoDecodingConfig(
				AutoDetectDecodingConfig.newBuilder().build()
			)
			.addLanguageCodes(properties.languageCode())
			.setModel(properties.google().model())
			.build();
	}

	private static String createRecognizerName(
		GoogleSpeechV2Properties properties
	) {
		GoogleSpeechV2Properties.Google google = properties.google();
		return RecognizerName.of(
			google.projectId(),
			google.location(),
			google.recognizerId()
		).toString();
	}
}
