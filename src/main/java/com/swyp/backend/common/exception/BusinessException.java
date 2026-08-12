package com.swyp.backend.common.exception;

import com.swyp.backend.common.response.ApiCode;
import lombok.Getter;

/**
 * Application exception carrying an {@link ApiCode}. One constructor — every feature throws this
 * with its own ApiCode enum value, and the global handler maps it to the standard error envelope.
 * (Intentionally not one subclass/constructor per feature: that couples this class to every domain.)
 */
@Getter
public class BusinessException extends RuntimeException {

	private final transient ApiCode code;

	public BusinessException(ApiCode code) {
		super(code.getMessage());
		this.code = code;
	}

	public BusinessException(ApiCode code, Throwable cause) {
		super(code.getMessage(), cause);
		this.code = code;
	}
}
