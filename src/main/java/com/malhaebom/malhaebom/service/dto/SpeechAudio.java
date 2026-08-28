package com.malhaebom.malhaebom.service.dto;

import java.util.Arrays;

public record SpeechAudio(byte[] content, String contentType) {
	public SpeechAudio {
		if (content == null) {
			throw new IllegalArgumentException("음성 파일 내용은 null일 수 없습니다.");
		}

		if (contentType == null || contentType.isBlank()) {
			throw new IllegalArgumentException("음성 파일 형식은 비어 있을 수 없습니다.");
		}

		content = Arrays.copyOf(content, content.length);
	}

	@Override
	public byte[] content() {
		return Arrays.copyOf(content, content.length);
	}

	public int size() {
		return content.length;
	}
}
