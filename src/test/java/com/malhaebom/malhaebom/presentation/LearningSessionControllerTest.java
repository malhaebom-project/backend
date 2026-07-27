package com.malhaebom.malhaebom.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.Difficulty;
import com.malhaebom.malhaebom.domain.LearningTopic;
import com.malhaebom.malhaebom.domain.Question;
import com.malhaebom.malhaebom.domain.QuestionType;
import com.malhaebom.malhaebom.domain.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.repository.QuestionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LearningSessionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private QuestionRepository questionRepository;

	@Autowired
	private LearningSessionRepository learningSessionRepository;

	@Test
	void 학습_세션_API를_호출한다() throws Exception {
		Question question = questionRepository.save(
			Question.create(
				LearningTopic.ANIMAL,
				Difficulty.EASY,
				QuestionType.PICTURE_DESCRIPTION,
				"What is the boy doing?",
				"남자아이는 무엇을 하고 있나요?",
				"https://cdn.example.com/questions/boy-running.png",
				"He is ____ing.",
				"https://cdn.example.com/tts/question.mp3"
			)
		);

		mockMvc.perform(
				post("/api/v1/learning-sessions")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{
						  "childId": 1,
						  "topicId": 1,
						  "difficulty": "EASY",
						  "questionTypes": ["PICTURE_DESCRIPTION"],
						  "questionCount": 1
						}
						""")
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.childId").value(1))
			.andExpect(jsonPath("$.data.topicId").value(1))
			.andExpect(jsonPath("$.data.currentQuestionIndex").value(0))
			.andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.message").value("학습을 시작합니다."));

		Long sessionId = learningSessionRepository.findAll().getFirst().getId();

		mockMvc.perform(
				get("/api/v1/learning-sessions/{sessionId}/questions/next", sessionId)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.questionId").value(question.getId()))
			.andExpect(jsonPath("$.data.questionIndex").value(1))
			.andExpect(jsonPath("$.data.totalQuestionCount").value(1))
			.andExpect(jsonPath("$.data.type").value("PICTURE_DESCRIPTION"))
			.andExpect(jsonPath("$.data.questionText").value("What is the boy doing?"));

		mockMvc.perform(get("/api/v1/learning-sessions/{sessionId}", sessionId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sessionId").value(sessionId))
			.andExpect(jsonPath("$.data.questionCount").value(1))
			.andExpect(jsonPath("$.data.correctCount").value(0));

		mockMvc.perform(
				post("/api/v1/learning-sessions/{sessionId}/complete", sessionId)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("COMPLETED"))
			.andExpect(jsonPath("$.message").value("학습을 완료했습니다."));
	}
}
