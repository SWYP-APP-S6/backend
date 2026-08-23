package com.swyp.backend.common.exception;

import com.swyp.backend.common.response.ApiCode;
import lombok.Getter;

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
