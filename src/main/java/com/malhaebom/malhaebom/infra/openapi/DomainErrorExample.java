package com.malhaebom.malhaebom.infra.openapi;

import com.malhaebom.malhaebom.global.exception.ErrorCode;

public @interface DomainErrorExample {
	ErrorCode code();

	String message();

	String name() default "";
}
