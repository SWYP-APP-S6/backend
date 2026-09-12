package com.swyp.backend.hold.dto;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldItem;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record OwnerHoldSummaryResponse(
		Long id,
		OwnerHoldStatus status,
		String nickname,
		int totalQty,
		List<OwnerHoldItem> items,
		Instant heldAt,
		Instant expiresAt) {

	public record OwnerHoldItem(Long productId, String productName, int qty) {

		static OwnerHoldItem from(HoldItem item) {
			return new OwnerHoldItem(
					item.getProduct().getId(), item.getProduct().getName(), item.getQty());
		}
	}

	public static OwnerHoldSummaryResponse from(Hold hold) {
		List<OwnerHoldItem> items = hold.getItems().stream()
				.sorted(Comparator.comparing(item -> item.getProduct().getId()))
				.map(OwnerHoldItem::from)
				.toList();
		return new OwnerHoldSummaryResponse(
				hold.getId(),
				OwnerHoldStatus.of(hold),
				hold.getUser().getNickname(),
				items.stream().mapToInt(OwnerHoldItem::qty).sum(),
				items,
				hold.getCreatedAt(),
				hold.getExpiresAt());
	}
}
