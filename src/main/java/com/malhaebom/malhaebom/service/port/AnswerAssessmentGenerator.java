package com.malhaebom.malhaebom.service.port;

import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;

public interface AnswerAssessmentGenerator {

	AnswerAssessmentTask generateAsync(AnswerAssessmentInput input);
}
