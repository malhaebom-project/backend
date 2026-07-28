package com.malhaebom.malhaebom.presentation;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.malhaebom.malhaebom.presentation.auth.AuthLoginUserArgumentResolver;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {

	private final AuthLoginUserArgumentResolver authLoginUserArgumentResolver;

	@Override
	public void addArgumentResolvers(
		List<HandlerMethodArgumentResolver> resolvers
	) {
		resolvers.add(authLoginUserArgumentResolver);
	}
}
