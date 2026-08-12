package com.swyp.backend.common.response;

/**
 * Standard success envelope — every successful response is wrapped as
 * {@code {status, code, message, data}}. Errors use {@link ErrorResponse} via the global handler.
 */
public record ApiResponse<T>(int status, String code, String message, T data) {

	public static <T> ApiResponse<T> of(SuccessCode code, T data) {
		return new ApiResponse<>(code.getStatus().value(), code.name(), code.getMessage(), data);
	}

	public static ApiResponse<Void> of(SuccessCode code) {
		return new ApiResponse<>(code.getStatus().value(), code.name(), code.getMessage(), null);
	}
}
