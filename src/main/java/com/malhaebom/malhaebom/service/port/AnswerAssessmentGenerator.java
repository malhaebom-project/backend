package com.malhaebom.malhaebom.service.port;

import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;

public interface AnswerAssessmentGenerator {

	AnswerAssessment generate(Question question, String answerText);
}
