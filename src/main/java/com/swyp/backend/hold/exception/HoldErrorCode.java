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
	ALREADY_HOLDING(HttpStatus.CONFLICT, "이미 찜한 상품입니다."),
	HOLD_ALREADY_RESOLVED(HttpStatus.CONFLICT, "이미 처리된 찜입니다."),
	HOLD_ALREADY_EXPIRED(HttpStatus.CONFLICT, "찜 시간이 지나 취소할 수 없습니다."),
	OTHER_STORE_HOLD_ACTIVE(HttpStatus.CONFLICT, "다른 가게에서 찜이 진행 중입니다."),
	CANCEL_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "취소 가능 횟수를 모두 사용했습니다."),
	PRODUCT_STOCK_GONE(HttpStatus.CONFLICT, "남은 재고가 없어 수령 처리를 할 수 없습니다.");

	private final HttpStatus status;
	private final String message;
}
