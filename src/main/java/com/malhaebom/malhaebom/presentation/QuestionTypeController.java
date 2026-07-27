package com.malhaebom.malhaebom.presentation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.QuestionTypeResponse;
import com.malhaebom.malhaebom.service.QuestionTypeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/question-types")
@RequiredArgsConstructor
public class QuestionTypeController {

	private final QuestionTypeService questionTypeService;

	@GetMapping
	public ApiResponse<List<QuestionTypeResponse>> getQuestionTypes() {
		List<QuestionTypeResponse> response = questionTypeService.getQuestionTypes().stream()
			.map(QuestionTypeResponse::from)
			.toList();

		return ApiResponse.success(response);
	}
}
