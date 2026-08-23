package com.swyp.backend.common.response;

public record ApiResponse<T>(int status, String code, String message, T data) {

	public static <T> ApiResponse<T> of(SuccessCode code, T data) {
		return new ApiResponse<>(code.getStatus().value(), code.name(), code.getMessage(), data);
	}

	public static ApiResponse<Void> of(SuccessCode code) {
		return new ApiResponse<>(code.getStatus().value(), code.name(), code.getMessage(), null);
	}
}
