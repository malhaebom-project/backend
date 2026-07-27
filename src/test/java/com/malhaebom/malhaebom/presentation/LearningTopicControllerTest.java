package com.malhaebom.malhaebom.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LearningTopicControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 학습_주제_목록을_조회한다() throws Exception {
		mockMvc.perform(get("/api/v1/learning-topics"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.data[0].topicId").value(1))
			.andExpect(jsonPath("$.data[0].name").value("동물"))
			.andExpect(jsonPath("$.data[0].code").value("ANIMAL"))
			.andExpect(jsonPath("$.data[1].topicId").value(2))
			.andExpect(jsonPath("$.data[1].name").value("음식"))
			.andExpect(jsonPath("$.data[1].code").value("FOOD"))
			.andExpect(jsonPath("$.data[2].topicId").value(3))
			.andExpect(jsonPath("$.data[2].name").value("일상생활"))
			.andExpect(jsonPath("$.data[2].code").value("DAILY_LIFE"))
			.andExpect(jsonPath("$.message").doesNotExist());
	}
}
