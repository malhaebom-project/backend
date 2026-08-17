package com.malhaebom.malhaebom.service.port;

import java.util.concurrent.CompletionStage;

import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;

public interface AnswerAssessmentGenerator {

	CompletionStage<AnswerAssessment> generateAsync(AnswerAssessmentInput input);
}
