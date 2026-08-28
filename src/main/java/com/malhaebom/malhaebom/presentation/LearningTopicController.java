package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.LearningTopicResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/learning-topics")
@Tag(name = "문제 조회")
public class LearningTopicController {
	@GetMapping
	@Operation(summary = "학습 주제 목록 조회")
	public ApiResponse<List<LearningTopicResponse>> getLearningTopics() {
		List<LearningTopicResponse> response = Stream.of(LearningTopic.values())
			.map(LearningTopicResponse::from)
			.toList();
		return ApiResponse.success(response);
	}
}
