package com.malhaebom.malhaebom.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.QuestionTtsResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

	private final QuestionRepository questionRepository;

	@GetMapping("/{questionId}/tts")
	public ApiResponse<QuestionTtsResponse> getTts(
		@PathVariable Long questionId
	) {
		Question question = questionRepository
			.findByIdAndActiveTrue(questionId)
			.orElseThrow(() -> new ApiException(ErrorCode.QUESTION_NOT_FOUND));

		return ApiResponse.success(QuestionTtsResponse.from(question));
	}
}
