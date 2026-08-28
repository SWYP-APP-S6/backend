package com.swyp.backend.store.exception;

import com.swyp.backend.common.response.ApiCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StoreErrorCode implements ApiCode {

	STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "가게를 찾을 수 없습니다."),
	CANNOT_REVERT_TO_PENDING(HttpStatus.BAD_REQUEST, "심사 대기 상태로 되돌릴 수 없습니다.");

	private final HttpStatus status;
	private final String message;
}
