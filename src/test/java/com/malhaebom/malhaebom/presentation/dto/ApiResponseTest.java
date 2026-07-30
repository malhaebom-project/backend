package com.malhaebom.malhaebom.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ApiResponseTest {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void 오류_응답은_메시지와_오류_코드를_포함한다() throws Exception {
		ApiResponse<Void> response = ApiResponse.error(
			"요청 값이 올바르지 않습니다.",
			"INVALID_REQUEST"
		);

		JsonNode json = jsonMapper.readTree(
			jsonMapper.writeValueAsString(response)
		);

		assertThat(json.get("success").asBoolean()).isFalse();
		assertThat(json.get("data").isNull()).isTrue();
		assertThat(json.get("message").asString())
			.isEqualTo("요청 값이 올바르지 않습니다.");
		assertThat(json.get("errorCode").asString())
			.isEqualTo("INVALID_REQUEST");
	}

	@Test
	void 성공_응답에는_오류_코드를_노출하지_않는다() throws Exception {
		ApiResponse<String> response = ApiResponse.success("result");

		JsonNode json = jsonMapper.readTree(
			jsonMapper.writeValueAsString(response)
		);

		assertThat(json.has("errorCode")).isFalse();
	}
}
