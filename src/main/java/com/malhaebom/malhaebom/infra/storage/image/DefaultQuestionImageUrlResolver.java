package com.malhaebom.malhaebom.infra.storage.image;

import com.malhaebom.malhaebom.service.port.QuestionImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultQuestionImageUrlResolver implements QuestionImageUrlResolver {
	private static final String HTTP_SCHEME = "http://";
	private static final String HTTPS_SCHEME = "https://";

	private final QuestionImageProperties properties;

	@Override
	public String resolve(String imagePath) {
		if (imagePath == null || imagePath.isBlank()) {
			return null;
		}

		if (isAbsoluteUrl(imagePath)) {
			return imagePath;
		}

		String baseUrl = properties.baseUrl();
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalStateException(
				"문제 이미지 base URL이 설정되지 않았습니다."
			);
		}

		return removeTrailingSlashes(baseUrl)
			+ "/"
			+ removeLeadingSlashes(imagePath);
	}

	private boolean isAbsoluteUrl(String imagePath) {
		return imagePath.startsWith(HTTP_SCHEME) || imagePath.startsWith(HTTPS_SCHEME);
	}

	private String removeTrailingSlashes(String value) {
		int endIndex = value.length();
		while (endIndex > 0 && value.charAt(endIndex - 1) == '/') {
			endIndex--;
		}
		return value.substring(0, endIndex);
	}

	private String removeLeadingSlashes(String value) {
		int startIndex = 0;
		while (startIndex < value.length() && value.charAt(startIndex) == '/') {
			startIndex++;
		}
		return value.substring(startIndex);
	}
}
