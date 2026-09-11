package com.swyp.backend.hold.exception;

import com.swyp.backend.common.response.ApiCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HoldErrorCode implements ApiCode {

	HOLD_NOT_FOUND(HttpStatus.NOT_FOUND, "찜을 찾을 수 없습니다."),
	INSUFFICIENT_QTY(HttpStatus.CONFLICT, "방금 마감됐어요. 남은 수량이 부족합니다."),
	PRODUCT_NOT_SELLABLE(HttpStatus.CONFLICT, "지금은 찜할 수 없는 상품입니다."),
	HOLD_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "한 번에 찜할 수 있는 수량을 넘었습니다."),
	ALREADY_HOLDING(HttpStatus.CONFLICT, "이미 찜한 상품입니다.");

	private final HttpStatus status;
	private final String message;
}
