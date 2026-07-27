package com.malhaebom.malhaebom.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.malhaebom.malhaebom.domain.LearningTopic;

@Service
public class LearningTopicService {

	public List<LearningTopic> getLearningTopics() {
		return List.of(LearningTopic.values());
	}
}
