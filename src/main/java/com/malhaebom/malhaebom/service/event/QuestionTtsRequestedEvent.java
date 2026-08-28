package com.malhaebom.malhaebom.service.event;

public record QuestionTtsRequestedEvent(Long questionId, String questionText) {}
