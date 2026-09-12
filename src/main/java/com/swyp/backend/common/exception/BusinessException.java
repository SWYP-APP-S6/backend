package com.swyp.backend.common.exception;

import com.swyp.backend.common.response.ApiCode;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

	private final transient ApiCode code;

	private final transient Instant retryAt;

	public BusinessException(ApiCode code) {
		this(code, (Instant) null);
	}

	public BusinessException(ApiCode code, @Nullable Instant retryAt) {
		super(code.getMessage());
		this.code = code;
		this.retryAt = retryAt;
	}

	public BusinessException(ApiCode code, Throwable cause) {
		super(code.getMessage(), cause);
		this.code = code;
		this.retryAt = null;
	}
}
