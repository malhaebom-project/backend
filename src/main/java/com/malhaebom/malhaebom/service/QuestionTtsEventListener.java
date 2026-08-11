package com.malhaebom.malhaebom.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.service.dto.TtsAudio;
import com.malhaebom.malhaebom.service.event.QuestionTtsRequestedEvent;
import com.malhaebom.malhaebom.service.port.QuestionTtsStorage;
import com.malhaebom.malhaebom.service.port.TtsClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
	prefix = "malhaebom.tts",
	name = "enabled",
	havingValue = "true"
)
public class QuestionTtsEventListener {

	private final QuestionRepository questionRepository;
	private final TtsClient ttsClient;
	private final QuestionTtsStorage questionTtsStorage;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(QuestionTtsRequestedEvent event) {
		try {
			Question question = questionRepository
				.findByIdAndActiveTrue(event.questionId())
				.orElse(null);
			if (question == null) {
				log.warn(
					"TTS를 저장할 문제를 찾을 수 없습니다. questionId={}",
					event.questionId()
				);
				return;
			}
			TtsAudio audio = ttsClient.generate(
				event.questionText()
			);
			String audioUrl = questionTtsStorage.upload(
				event.questionId(),
				audio
			);
			question.updateTtsUrl(audioUrl);
			questionRepository.save(question);
		} catch (RuntimeException exception) {
			log.error(
				"문제 TTS 생성에 실패했습니다. questionId={}",
				event.questionId(),
				exception
			);
		}
	}
}
