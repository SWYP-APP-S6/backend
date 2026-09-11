package com.swyp.backend.home.dto;

import com.swyp.backend.hold.entity.Hold;
import java.time.Instant;

public record OwnerHomeVisit(
		Long holdId, String nickname, String productName, int qty, Instant expiresAt) {

	public static OwnerHomeVisit from(Hold hold) {
		return new OwnerHomeVisit(
				hold.getId(),
				hold.getUser().getNickname(),
				hold.getProduct().getName(),
				hold.getQty(),
				hold.getExpiresAt());
	}
}
