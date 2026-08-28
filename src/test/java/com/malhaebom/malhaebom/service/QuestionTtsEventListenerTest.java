package com.malhaebom.malhaebom.service;

import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.service.event.QuestionTtsRequestedEvent;
import com.malhaebom.malhaebom.service.port.QuestionTtsStorage;
import com.malhaebom.malhaebom.service.port.TtsClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionTtsEventListenerTest {
	@Mock
	private QuestionRepository questionRepository;
	@Mock
	private TtsClient ttsClient;
	@Mock
	private QuestionTtsStorage questionTtsStorage;
	@InjectMocks
	private QuestionTtsEventListener listener;

	@Test
	void 문제가_없으면_TTS를_생성하거나_업로드하지_않는다() {
		QuestionTtsRequestedEvent event =
			new QuestionTtsRequestedEvent(999L, "Question text");
		when(questionRepository.findByIdAndActiveTrue(999L))
			.thenReturn(Optional.empty());

		listener.handle(event);

		verify(ttsClient, never()).generate(event.questionText());
		verify(questionTtsStorage, never()).upload(anyLong(), any());
	}
}
