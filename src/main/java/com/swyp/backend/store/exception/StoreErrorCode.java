package com.swyp.backend.store.exception;

import com.swyp.backend.common.response.ApiCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StoreErrorCode implements ApiCode {

	STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "가게를 찾을 수 없습니다."),
	CANNOT_REVERT_TO_PENDING(HttpStatus.BAD_REQUEST, "심사 대기 상태로 되돌릴 수 없습니다."),
	STORE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 등록된 가게가 있습니다."),
	OWNER_ROLE_REQUIRED(HttpStatus.FORBIDDEN, "점주 계정만 가게를 등록할 수 있습니다."),
	GEOCODING_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "주소를 찾을 수 없습니다. 주소를 다시 확인해 주세요."),
	GEOCODING_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "주소 변환 서비스와 통신할 수 없습니다."),
	STORE_NOT_REGISTERED(HttpStatus.NOT_FOUND, "등록된 가게가 없습니다.");

	private final HttpStatus status;
	private final String message;
}
