package com.malhaebom.malhaebom.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.malhaebom.malhaebom.domain.QuestionType;

@Service
public class QuestionTypeService {

	public List<QuestionType> getQuestionTypes() {
		return List.of(QuestionType.values());
	}
}
