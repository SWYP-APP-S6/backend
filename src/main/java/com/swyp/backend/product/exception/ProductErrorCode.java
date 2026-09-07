package com.swyp.backend.product.exception;

import com.swyp.backend.common.response.ApiCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ApiCode {

	PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
	INVALID_PRICE(HttpStatus.BAD_REQUEST, "할인가는 정가보다 낮아야 합니다."),
	DISPOSITION_REQUIRED(HttpStatus.BAD_REQUEST, "진행 중인 찜이 있어 처리 방법을 선택해야 합니다.");

	private final HttpStatus status;
	private final String message;
}
