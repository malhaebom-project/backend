package com.malhaebom.malhaebom.domain;

import java.util.Arrays;

public enum LearningTopic {

	ANIMAL(1L, "동물"),
	FOOD(2L, "음식"),
	DAILY_LIFE(3L, "일상생활");

	private final Long topicId;
	private final String name;

	LearningTopic(Long topicId, String name) {
		this.topicId = topicId;
		this.name = name;
	}

	public Long getTopicId() {
		return topicId;
	}

	public String getName() {
		return name;
	}

	public String getCode() {
		return name();
	}

	public static LearningTopic fromTopicId(Long topicId) {
		return Arrays.stream(values())
			.filter(topic -> topic.topicId.equals(topicId))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 학습 주제입니다."));
	}
}
