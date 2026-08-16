package com.malhaebom.malhaebom.infra.speech;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.speech.v2.AutoDetectDecodingConfig;
import com.google.cloud.speech.v2.RecognitionConfig;
import com.google.cloud.speech.v2.RecognizeRequest;
import com.google.cloud.speech.v2.RecognizeResponse;
import com.google.cloud.speech.v2.RecognizerName;
import com.google.cloud.speech.v2.PhraseSet;
import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechAdaptation;
import com.google.cloud.speech.v2.SpeechRecognitionAlternative;
import com.google.protobuf.ByteString;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

public class GoogleSpeechV2Transcriber implements SpeechTranscriber {

	public static final String PROVIDER = "GOOGLE_CLOUD_STT_V2";

	private static final int CONFIDENCE_SCALE = 4;
	private static final int MAX_ADAPTATION_PHRASES = 1_200;
	private static final int MAX_PHRASE_LENGTH = 100;

	private final SpeechClient client;
	private final RecognitionConfig recognitionConfig;
	private final String recognizer;
	private final float adaptationBoost;

	public GoogleSpeechV2Transcriber(
		SpeechClient client,
		GoogleSpeechV2Properties properties,
		String projectId
	) {
		this.client = client;
		this.recognitionConfig = createRecognitionConfig(properties);
		this.recognizer = createRecognizerName(properties, projectId);
		this.adaptationBoost = properties.adaptationBoost();
	}

	@Override
	public String provider() {
		return PROVIDER;
	}

	@Override
	public SpeechTranscriptionResult transcribe(
		SpeechAudio audio,
		List<String> adaptationPhrases
	) {
		RecognizeRequest request = RecognizeRequest.newBuilder()
			.setRecognizer(recognizer)
			.setConfig(createRequestConfig(adaptationPhrases))
			.setContent(ByteString.copyFrom(audio.content()))
			.build();

		RecognizeResponse response;
		try {
			response = client.recognize(request);
		} catch (com.google.api.gax.rpc.ApiException exception) {
			throw mapGoogleException(exception);
		} catch (RuntimeException exception) {
			throw new ApiException(
				ErrorCode.STT_PROCESSING_FAILED,
				exception
			);
		}

		return normalize(response);
	}

	private RecognitionConfig createRequestConfig(
		List<String> adaptationPhrases
	) {
		List<String> phrases = adaptationPhrases.stream()
			.filter(phrase -> phrase != null && !phrase.isBlank())
			.map(String::strip)
			.filter(phrase -> phrase.length() <= MAX_PHRASE_LENGTH)
			.distinct()
			.limit(MAX_ADAPTATION_PHRASES)
			.toList();
		if (phrases.isEmpty()) {
			return recognitionConfig;
		}

		PhraseSet.Builder phraseSet = PhraseSet.newBuilder();
		phrases.stream()
			.map(phrase -> PhraseSet.Phrase.newBuilder()
				.setValue(phrase)
				.setBoost(adaptationBoost)
				.build())
			.forEach(phraseSet::addPhrases);
		SpeechAdaptation adaptation = SpeechAdaptation.newBuilder()
			.addPhraseSets(
				SpeechAdaptation.AdaptationPhraseSet.newBuilder()
					.setInlinePhraseSet(phraseSet)
			)
			.build();
		return recognitionConfig.toBuilder()
			.setAdaptation(adaptation)
			.build();
	}

	private SpeechTranscriptionResult normalize(RecognizeResponse response) {
		if (response == null) {
			throw new ApiException(ErrorCode.STT_PROCESSING_FAILED);
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
			throw new ApiException(ErrorCode.SPEECH_NOT_RECOGNIZED);
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

	private ApiException mapGoogleException(
		com.google.api.gax.rpc.ApiException exception
	) {
		StatusCode statusCode = exception.getStatusCode();
		StatusCode.Code code = statusCode == null
			? null
			: statusCode.getCode();

		if (code == StatusCode.Code.RESOURCE_EXHAUSTED) {
			return new ApiException(
				ErrorCode.AI_REQUEST_LIMIT_EXCEEDED,
				exception
			);
		}
		if (code == StatusCode.Code.DEADLINE_EXCEEDED) {
			return new ApiException(
				ErrorCode.STT_PROCESSING_TIMEOUT,
				exception
			);
		}
		return new ApiException(ErrorCode.STT_PROCESSING_FAILED, exception);
	}

	private static RecognitionConfig createRecognitionConfig(
		GoogleSpeechV2Properties properties
	) {
		return RecognitionConfig.newBuilder()
			.setAutoDecodingConfig(
				AutoDetectDecodingConfig.newBuilder().build()
			)
			.addLanguageCodes(properties.languageCode())
			.setModel(properties.model())
			.build();
	}

	private static String createRecognizerName(
		GoogleSpeechV2Properties properties,
		String projectId
	) {
		return RecognizerName.of(
			projectId,
			properties.location(),
			properties.recognizerId()
		).toString();
	}
}
