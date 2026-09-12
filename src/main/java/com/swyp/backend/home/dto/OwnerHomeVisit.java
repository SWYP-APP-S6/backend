package com.swyp.backend.home.dto;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldItem;
import java.time.Instant;
import java.util.stream.Collectors;

public record OwnerHomeVisit(
		Long holdId, String nickname, String summary, int totalQty, Instant expiresAt) {

	public static OwnerHomeVisit from(Hold hold) {
		return new OwnerHomeVisit(
				hold.getId(),
				hold.getUser().getNickname(),
				hold.getItems().stream()
						.map(item -> item.getProduct().getName())
						.collect(Collectors.joining(", ")),
				hold.getItems().stream().mapToInt(HoldItem::getQty).sum(),
				hold.getExpiresAt());
	}
}
