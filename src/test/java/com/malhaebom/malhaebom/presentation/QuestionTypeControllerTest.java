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
class QuestionTypeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 문제_유형_목록을_조회한다() throws Exception {
		mockMvc.perform(get("/api/v1/question-types"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.data[0].code").value("SHORT_ANSWER"))
			.andExpect(jsonPath("$.data[0].name").value("단어 말하기"))
			.andExpect(jsonPath("$.data[1].code").value("PICTURE_DESCRIPTION"))
			.andExpect(jsonPath("$.data[1].name").value("그림 보고 말하기"))
			.andExpect(jsonPath("$.data[2].code").value("OPEN_SPEAKING"))
			.andExpect(jsonPath("$.data[2].name").value("말로 설명하기"))
			.andExpect(jsonPath("$.message").doesNotExist());
	}
}
