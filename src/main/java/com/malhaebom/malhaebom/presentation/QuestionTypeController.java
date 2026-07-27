package com.malhaebom.malhaebom.presentation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malhaebom.malhaebom.domain.QuestionType;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.QuestionTypeResponse;

@RestController
@RequestMapping("/api/v1/question-types")
public class QuestionTypeController {

	@GetMapping
	public ApiResponse<List<QuestionTypeResponse>> getQuestionTypes() {
		List<QuestionTypeResponse> response = List.of(QuestionType.values()).stream()
			.map(QuestionTypeResponse::from)
			.toList();

		return ApiResponse.success(response);
	}
}
