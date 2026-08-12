package com.swyp.backend.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Generic, cross-feature error codes thrown via {@code BusinessException} (plus the two the global
 * handler uses itself). Feature-specific codes live in that feature's own enum implementing
 * {@link ApiCode} — do not add domain codes here. Framework client errors (405, 415, unreadable
 * body, …) are not listed here: the global handler renders them from their HTTP status.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode implements ApiCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값 검증에 실패했습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
	CONFLICT(HttpStatus.CONFLICT, "이미 존재하거나 충돌하는 요청입니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다.");

	private final HttpStatus status;
	private final String message;
}
