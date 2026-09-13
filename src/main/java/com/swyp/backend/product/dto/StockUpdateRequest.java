package com.swyp.backend.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record StockUpdateRequest(
		/** 매장에 실제로 남아 있는 총 수량. 찜된 것까지 포함한다. */
		@Min(0) @Max(9999) int stockQty,
		/** 찜이 그 수량을 넘으면 선착순으로 넘치는 찜을 취소할지. 「나중에 하기」면 false. */
		boolean cancelOverflow) {
}
