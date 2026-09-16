package com.swyp.backend.hold.dto;

import java.time.Instant;
import java.util.List;

public record OwnerHoldCancelCandidatesResponse(
		int productsShortOfStock,
		int suggestedCancelCount,
		String noticeMessage,
		List<OwnerHoldCancelProduct> products) {

	public record OwnerHoldCancelProduct(
			Long productId,
			String productName,
			int stockQty,
			int heldQty,
			int shortfallQty,
			List<OwnerHoldCancelCandidate> holds) {
	}

	public record OwnerHoldCancelCandidate(
			Long holdId,
			int heldOrder,
			Instant heldAt,
			String nickname,
			int qty,
			int lineTotal,
			boolean suggested) {
	}
}
