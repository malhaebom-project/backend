package com.malhaebom.malhaebom.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthCodeExchangeRequest(
        @NotBlank(message = "OAuth 로그인 코드는 필수입니다.")
        String code
) {
}
