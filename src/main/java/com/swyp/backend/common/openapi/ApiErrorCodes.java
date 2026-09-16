package com.swyp.backend.common.openapi;

import com.swyp.backend.common.response.ApiCode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiErrorCodes.List.class)
public @interface ApiErrorCodes {

	Class<? extends ApiCode> in();

	String[] codes();

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface List {

		ApiErrorCodes[] value();
	}
}
