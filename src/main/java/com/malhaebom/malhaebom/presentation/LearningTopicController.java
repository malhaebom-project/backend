package com.malhaebom.malhaebom.presentation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.LearningTopicResponse;
import com.malhaebom.malhaebom.service.LearningTopicService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/learning-topics")
@RequiredArgsConstructor
public class LearningTopicController {

	private final LearningTopicService learningTopicService;

	@GetMapping
	public ApiResponse<List<LearningTopicResponse>> getLearningTopics() {
		List<LearningTopicResponse> response = learningTopicService.getLearningTopics().stream()
			.map(LearningTopicResponse::from)
			.toList();

		return ApiResponse.success(response);
	}
}
