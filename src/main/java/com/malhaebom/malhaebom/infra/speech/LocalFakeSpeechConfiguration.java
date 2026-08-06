package com.malhaebom.malhaebom.infra.speech;

import java.util.Set;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

@Configuration(proxyBeanMethods = false)
@Profile("local-fake-stt")
public class LocalFakeSpeechConfiguration {

	private static final String QUESTION_TEXT = "What is the boy doing?";
	private static final String QUESTION_TTS_URL =
		"local-fake://question-tts";
	private static final String TRANSCRIPT = "He is running.";

	@Bean
	@Primary
	SpeechTranscriber localFakeSpeechTranscriber() {
		return new SpeechTranscriber() {

			@Override
			public String provider() {
				return "LOCAL_FAKE_STT";
			}

			@Override
			public SpeechTranscriptionResult transcribe(SpeechAudio audio) {
				return new SpeechTranscriptionResult(
					TRANSCRIPT,
					0.94,
					provider()
				);
			}
		};
	}

	@Bean
	ApplicationRunner localFakeQuestionInitializer(
		QuestionRepository questionRepository
	) {
		return arguments -> {
			boolean alreadyExists = questionRepository
				.findAllByActiveTrueOrderByIdDesc()
				.stream()
				.anyMatch(question ->
					question.getTopic() == LearningTopic.DAILY_LIFE
						&& question.getDifficulty() == Difficulty.EASY
						&& question.getType()
							== QuestionType.PICTURE_DESCRIPTION
						&& QUESTION_TEXT.equals(question.getQuestionText())
				);

			if (!alreadyExists) {
				questionRepository.save(
					Question.create(
						LearningTopic.DAILY_LIFE,
						Difficulty.EASY,
						QuestionType.PICTURE_DESCRIPTION,
						QUESTION_TEXT,
						"남자아이는 무엇을 하고 있나요?",
						null,
						"The boy is running.",
						Set.of(TRANSCRIPT),
						"He is ____ing.",
						QUESTION_TTS_URL
					)
				);
			}
		};
	}
}
