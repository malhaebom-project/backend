package com.malhaebom.malhaebom.service.port;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.service.dto.AnswerFeedback;

public interface AnswerFeedbackGenerator {

	AnswerFeedback generate(
		Question question,
		String answerText,
		AnswerResult result
	);
}
