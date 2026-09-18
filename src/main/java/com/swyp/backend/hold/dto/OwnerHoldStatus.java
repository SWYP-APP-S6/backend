package com.swyp.backend.hold.dto;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCanceledBy;
import com.swyp.backend.hold.entity.HoldStatus;
import org.jspecify.annotations.Nullable;

public enum OwnerHoldStatus {
	HOLDING,
	COMPLETED,
	EXPIRED,
	CANCELED_BY_OWNER,
	CANCELED_BY_USER;

	public static OwnerHoldStatus of(Hold hold) {
		return of(hold.getStatus(), hold.getCanceledBy());
	}

	public static OwnerHoldStatus of(HoldStatus status, @Nullable HoldCanceledBy canceledBy) {
		return switch (status) {
			case HOLDING -> HOLDING;
			case COMPLETED -> COMPLETED;
			case EXPIRED -> EXPIRED;
			case CANCELED -> canceledBy == HoldCanceledBy.OWNER ? CANCELED_BY_OWNER : CANCELED_BY_USER;
		};
	}
}
