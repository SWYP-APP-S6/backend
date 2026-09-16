package com.swyp.backend.common.openapi;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Parameter(in = ParameterIn.QUERY, name = "page", description = "0부터 시작하는 페이지 번호",
		schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
@Parameter(in = ParameterIn.QUERY, name = "size", description = "한 페이지에 담을 개수",
		schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
				defaultValue = "20"))
public @interface PageQueryParams {
}
