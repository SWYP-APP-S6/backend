package com.swyp.backend.shorts.exception;

import com.swyp.backend.common.response.ApiCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ShortsErrorCode implements ApiCode {

	SHORTS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "숏폼 생성 서비스에 연결할 수 없습니다."),
	SHORTS_REQUEST_REJECTED(HttpStatus.BAD_REQUEST, "숏폼 생성 요청이 거부되었습니다."),
	SHORTS_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 숏폼 리소스를 찾을 수 없습니다.");

	private final HttpStatus status;
	private final String message;
}
