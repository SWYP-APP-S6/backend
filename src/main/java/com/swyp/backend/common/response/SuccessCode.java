package com.swyp.backend.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessCode {

	OK(HttpStatus.OK, "요청을 처리했습니다."),
	CREATED(HttpStatus.CREATED, "생성했습니다.");

	private final HttpStatus status;
	private final String message;
}
