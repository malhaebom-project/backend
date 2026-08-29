package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.QuestionTtsResponse;
import com.malhaebom.malhaebom.infra.openapi.DomainErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
@Tag(name = "문제 조회")
public class QuestionController {
	private final QuestionRepository questionRepository;

	@GetMapping("/{questionId}/tts")
	@Operation(summary = "문제 음성 조회")
	@DomainErrorResponses(ErrorCode.QUESTION_NOT_FOUND)
	public ApiResponse<QuestionTtsResponse> getTts(@PathVariable Long questionId) {
		Question question = questionRepository
			.findByIdAndActiveTrue(questionId)
			.orElseThrow(() -> new ApiException(ErrorCode.QUESTION_NOT_FOUND));
		return ApiResponse.success(QuestionTtsResponse.from(question));
	}
}
