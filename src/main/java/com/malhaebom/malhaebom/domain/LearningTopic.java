package com.malhaebom.malhaebom.domain;

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
}
