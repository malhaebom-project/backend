package com.malhaebom.malhaebom.infra.openapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RefreshCookieResponse {
	Action value() default Action.ISSUE;

	enum Action {
		ISSUE,
		EXPIRE
	}
}
