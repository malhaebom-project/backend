package com.malhaebom.malhaebom.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record SubmitAnswerRequest(@NotNull Long speechAnswerId) {}
