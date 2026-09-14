package com.swyp.backend.home.dto;

public record OwnerHomeIssues(
		int expiredTodayCount,
		int productsShortOfStock,
		int shortfallQty) {
}
