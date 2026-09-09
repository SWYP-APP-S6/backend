package com.swyp.backend.product.dto;

import java.util.List;

public record OwnerHomeResponse(
		Summary summary,
		int reconfirmPendingCount,
		long activeHoldCount,
		List<ProductSummaryResponse> products) {

	public record Summary(int registeredCount, int heldQty, long completedQty) {
	}
}
