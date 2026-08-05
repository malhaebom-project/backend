package com.malhaebom.malhaebom.infra.storage.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QuestionImageUrlResolverTest {

	private static final String BASE_URL =
		"https://assets.example.com";

	@Test
	void 상대_경로에_base_url을_붙인다() {
		QuestionImageUrlResolver resolver = createResolver(BASE_URL);

		String imageUrl = resolver.resolve(
			"/question-images/easy/animal/cat.webp"
		);

		assertThat(imageUrl).isEqualTo(
			"https://assets.example.com/question-images/easy/animal/cat.webp"
		);
	}

	@Test
	void 중복_슬래시를_제거한다() {
		QuestionImageUrlResolver resolver = createResolver(BASE_URL + "//");

		String imageUrl = resolver.resolve("//question-images/cat.webp");

		assertThat(imageUrl).isEqualTo(
			"https://assets.example.com/question-images/cat.webp"
		);
	}

	@Test
	void 이미_완성된_URL은_그대로_반환한다() {
		QuestionImageUrlResolver resolver = createResolver(null);
		String absoluteUrl = "https://legacy.example.com/cat.webp";

		assertThat(resolver.resolve(absoluteUrl)).isEqualTo(absoluteUrl);
	}

	@Test
	void 이미지_경로가_없으면_null을_반환한다() {
		QuestionImageUrlResolver resolver = createResolver(BASE_URL);

		assertThat(resolver.resolve(null)).isNull();
		assertThat(resolver.resolve("  ")).isNull();
	}

	@Test
	void 상대_경로인데_base_url이_없으면_예외가_발생한다() {
		QuestionImageUrlResolver resolver = createResolver(null);

		assertThatThrownBy(() -> resolver.resolve("/question-images/cat.webp"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("문제 이미지 base URL이 설정되지 않았습니다.");
	}

	private QuestionImageUrlResolver createResolver(String baseUrl) {
		return new QuestionImageUrlResolver(
			new QuestionImageProperties(baseUrl)
		);
	}
}
