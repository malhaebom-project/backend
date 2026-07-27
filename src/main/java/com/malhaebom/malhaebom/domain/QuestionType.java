package com.malhaebom.malhaebom.domain;

public enum QuestionType {

	SHORT_ANSWER("단어 말하기"),
	PICTURE_DESCRIPTION("그림 보고 말하기"),
	OPEN_SPEAKING("말로 설명하기");

	private final String name;

	QuestionType(String name) {
		this.name = name;
	}

	public String getCode() {
		return name();
	}

	public String getName() {
		return name;
	}
}
