package com.swyp.backend.home.dto;

public record OwnerHomeIssues(
		long expiredTodayCount,
		/** 찜이 매장 실제 수량을 넘은 상품 수. 「상품 N개의 재고가 부족해요」. */
		int productsShortOfStock,
		/** 그 상품들에서 모자란 수량 합계. 선착순 취소 대상이다. */
		int shortfallQty) {
}
