package com.swyp.backend.hold.dto;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldItem;
import com.swyp.backend.product.entity.Product;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record OwnerHoldDetailResponse(
		Long id,
		OwnerHoldStatus status,
		String nickname,
		String storeName,
		int totalQty,
		int totalPrice,
		List<OwnerHoldItem> items,
		Instant heldAt,
		Instant expiresAt,
		Instant serverTime,
		@Nullable Instant completedAt,
		@Nullable Instant canceledAt,
		@Nullable String cancelReason) {

	public record OwnerHoldItem(
			Long productId,
			String productName,
			String photoUrl,
			int qty,
			int unitPrice,
			int lineTotal) {

		static OwnerHoldItem from(HoldItem item) {
			Product product = item.getProduct();
			return new OwnerHoldItem(
					product.getId(),
					product.getName(),
					product.getPhotoUrl(),
					item.getQty(),
					product.getSalePrice(),
					product.getSalePrice() * item.getQty());
		}
	}

	public static OwnerHoldDetailResponse from(Hold hold, Instant serverTime) {
		List<OwnerHoldItem> items = hold.getItems().stream()
				.sorted(Comparator.comparing(item -> item.getProduct().getId()))
				.map(OwnerHoldItem::from)
				.toList();
		return new OwnerHoldDetailResponse(
				hold.getId(),
				OwnerHoldStatus.of(hold),
				hold.getUser().getNickname(),
				hold.getStore().getName(),
				items.stream().mapToInt(OwnerHoldItem::qty).sum(),
				items.stream().mapToInt(OwnerHoldItem::lineTotal).sum(),
				items,
				hold.getCreatedAt(),
				hold.getExpiresAt(),
				serverTime,
				hold.getCompletedAt(),
				hold.getCanceledAt(),
				hold.getCancelReason());
	}
}
