package com.malhaebom.malhaebom.presentation.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.auth.jwt.JwtProperties;
import com.malhaebom.malhaebom.infra.auth.jwt.JwtProvider;
import com.malhaebom.malhaebom.infra.auth.jwt.JwtUserPayload;
import com.malhaebom.malhaebom.service.dto.LoginUser;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class AuthLoginUserArgumentResolver
	implements HandlerMethodArgumentResolver {

	private final JwtProperties properties;
	private final JwtProvider jwtProvider;
	private final HeaderProvider headerProvider;

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(Auth.class)
			&& parameter.getParameterType().equals(LoginUser.class);
	}

	@Override
	public Object resolveArgument(
		MethodParameter parameter,
		ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest,
		WebDataBinderFactory binderFactory
	) {
		HttpServletRequest request =
			webRequest.getNativeRequest(HttpServletRequest.class);
		String accessToken = headerProvider.extractAccessToken(
			request.getHeader(HttpHeaders.AUTHORIZATION)
		);

		if (jwtProvider.isTokenExpired(
			accessToken,
			properties.access().signingKey()
		)) {
			throw new ApiException(
				ErrorCode.UNAUTHORIZED,
				"만료된 액세스 토큰입니다."
			);
		}

		JwtUserPayload payload =
			jwtProvider.parsePayload(
				accessToken,
				properties.access().signingKey()
			);
		return new LoginUser(payload.userId());
	}
}
