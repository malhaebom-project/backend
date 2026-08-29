package com.malhaebom.malhaebom.infra.openapi;

import com.malhaebom.malhaebom.global.exception.ErrorCode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DomainErrorResponses {
	ErrorCode[] value() default {};

	DomainErrorExample[] examples() default {};
}
