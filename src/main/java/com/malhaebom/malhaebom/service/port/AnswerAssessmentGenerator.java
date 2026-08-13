package com.malhaebom.malhaebom.service.port;

import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;

public interface AnswerAssessmentGenerator {

	AnswerAssessment generate(AnswerAssessmentInput input);
}
