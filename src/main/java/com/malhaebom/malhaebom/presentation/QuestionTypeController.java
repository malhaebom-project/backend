package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.QuestionTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/question-types")
@Tag(name = "문제 조회", description = "문제 유형과 학습 주제 등 문제 관련 데이터를 조회하는 API")
public class QuestionTypeController {
	@GetMapping
	@Operation(summary = "문제 유형 목록 조회")
	public ApiResponse<List<QuestionTypeResponse>> getQuestionTypes() {
		List<QuestionTypeResponse> response = Stream.of(QuestionType.values())
			.map(QuestionTypeResponse::from)
			.toList();
		return ApiResponse.success(response);
	}
}
