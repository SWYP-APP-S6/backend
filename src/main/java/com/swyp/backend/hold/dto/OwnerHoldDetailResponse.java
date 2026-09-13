package com.swyp.backend.hold.dto;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.product.entity.Product;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record OwnerHoldDetailResponse(
		Long groupId,
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
			Long holdId,
			Long productId,
			String productName,
			String photoUrl,
			int qty,
			int unitPrice,
			int lineTotal) {

		static OwnerHoldItem from(Hold hold) {
			Product product = hold.getProduct();
			return new OwnerHoldItem(
					hold.getId(),
					product.getId(),
					product.getName(),
					product.getPhotoUrl(),
					hold.getQty(),
					product.getSalePrice(),
					product.getSalePrice() * hold.getQty());
		}
	}

	public static OwnerHoldDetailResponse of(List<Hold> group, Instant serverTime) {
		List<OwnerHoldItem> items = group.stream()
				.sorted(Comparator.comparing(held -> held.getProduct().getId()))
				.map(OwnerHoldItem::from)
				.toList();
		Hold hold = group.stream().min(Comparator.comparing(Hold::getId)).orElseThrow();
		return new OwnerHoldDetailResponse(
				hold.getGroupId(),
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
