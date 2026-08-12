package com.swyp.backend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Standard error envelope, mirroring {@link ApiResponse}'s shape. {@code fieldErrors} carries
 * per-field validation messages and is omitted from the JSON when empty.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(int status, String code, String message, Map<String, String> fieldErrors) {

	public static ErrorResponse of(ApiCode code) {
		return new ErrorResponse(code.getStatus().value(), code.name(), code.getMessage(), null);
	}

	public static ErrorResponse of(ApiCode code, Map<String, String> fieldErrors) {
		return new ErrorResponse(code.getStatus().value(), code.name(), code.getMessage(), fieldErrors);
	}
}
