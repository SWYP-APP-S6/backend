package com.swyp.backend.hold.dto;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.product.entity.Product;
import java.time.Instant;

public record OwnerHoldSummaryResponse(
		Long id,
		OwnerHoldStatus status,
		String nickname,
		Long productId,
		String productName,
		int qty,
		Instant heldAt,
		Instant expiresAt) {

	public static OwnerHoldSummaryResponse from(Hold hold) {
		Product product = hold.getProduct();
		return new OwnerHoldSummaryResponse(
				hold.getId(),
				OwnerHoldStatus.of(hold),
				hold.getUser().getNickname(),
				product.getId(),
				product.getName(),
				hold.getQty(),
				hold.getCreatedAt(),
				hold.getExpiresAt());
	}
}
