package com.swyp.backend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
		int status,
		String code,
		String message,
		@Nullable Map<String, String> fieldErrors,
		@Nullable Instant retryAt) {

	public static ErrorResponse of(ApiCode code) {
		return new ErrorResponse(code.getStatus().value(), code.name(), code.getMessage(), null, null);
	}

	public static ErrorResponse of(ApiCode code, Map<String, String> fieldErrors) {
		return new ErrorResponse(
				code.getStatus().value(), code.name(), code.getMessage(), fieldErrors, null);
	}

	public static ErrorResponse of(ApiCode code, @Nullable Instant retryAt) {
		return new ErrorResponse(code.getStatus().value(), code.name(), code.getMessage(), null, retryAt);
	}
}
