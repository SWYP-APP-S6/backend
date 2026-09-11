package com.swyp.backend.hold.dto;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.product.entity.Product;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record OwnerHoldDetailResponse(
		Long id,
		OwnerHoldStatus status,
		String nickname,
		String storeName,
		Long productId,
		String productName,
		String photoUrl,
		int qty,
		int unitPrice,
		int totalPrice,
		Instant heldAt,
		Instant expiresAt,
		Instant serverTime,
		@Nullable Instant completedAt,
		@Nullable Instant canceledAt,
		@Nullable String cancelReason) {

	public static OwnerHoldDetailResponse from(Hold hold, Instant serverTime) {
		Product product = hold.getProduct();
		return new OwnerHoldDetailResponse(
				hold.getId(),
				OwnerHoldStatus.of(hold),
				hold.getUser().getNickname(),
				product.getStore().getName(),
				product.getId(),
				product.getName(),
				product.getPhotoUrl(),
				hold.getQty(),
				product.getSalePrice(),
				product.getSalePrice() * hold.getQty(),
				hold.getCreatedAt(),
				hold.getExpiresAt(),
				serverTime,
				hold.getCompletedAt(),
				hold.getCanceledAt(),
				hold.getCancelReason());
	}
}
