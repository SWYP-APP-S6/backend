package com.swyp.backend.hold.dto;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCanceledBy;
import com.swyp.backend.hold.entity.HoldStatus;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record AdminHoldResponse(
		Long id,
		Long groupId,
		HoldStatus status,
		Ref user,
		Ref store,
		Ref product,
		int qty,
		int salePrice,
		int lineTotal,
		Instant heldAt,
		Instant expiresAt,
		@Nullable Instant completedAt,
		@Nullable Instant canceledAt,
		@Nullable HoldCanceledBy canceledBy,
		@Nullable String cancelReason,
		@Nullable Instant noShowChargedAt) {

	public record Ref(Long id, String name) {}

	public static AdminHoldResponse from(Hold hold, Instant now) {
		return new AdminHoldResponse(
				hold.getId(),
				hold.getGroupId(),
				hold.statusAt(now),
				new Ref(hold.getUser().getId(), hold.getUser().getNickname()),
				new Ref(hold.getStore().getId(), hold.getStore().getName()),
				new Ref(hold.getProduct().getId(), hold.getProduct().getName()),
				hold.getQty(),
				hold.getProduct().getSalePrice(),
				hold.getProduct().getSalePrice() * hold.getQty(),
				hold.getCreatedAt(),
				hold.getExpiresAt(),
				hold.getCompletedAt(),
				hold.getCanceledAt(),
				hold.getCanceledBy(),
				hold.getCancelReason(),
				hold.getNoShowChargedAt());
	}
}
