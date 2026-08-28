package com.malhaebom.malhaebom.support;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.service.dto.LoginUser;

public class StubLoginUserArgumentResolver implements HandlerMethodArgumentResolver {
	private final Long userId;

	public StubLoginUserArgumentResolver(Long userId) {
		this.userId = userId;
	}

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
		return new LoginUser(userId);
	}
}
