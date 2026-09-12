package com.swyp.backend.hold.dto;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCanceledBy;
import com.swyp.backend.hold.entity.HoldStatus;
import org.jspecify.annotations.Nullable;

public enum OwnerHoldStatus {
	HOLDING(HoldStatus.HOLDING, null),
	COMPLETED(HoldStatus.COMPLETED, null),
	EXPIRED(HoldStatus.EXPIRED, null),
	CANCELED_BY_OWNER(HoldStatus.CANCELED, HoldCanceledBy.OWNER),
	CANCELED_BY_USER(HoldStatus.CANCELED, HoldCanceledBy.USER);

	private final HoldStatus status;
	private final HoldCanceledBy canceledBy;

	OwnerHoldStatus(HoldStatus status, @Nullable HoldCanceledBy canceledBy) {
		this.status = status;
		this.canceledBy = canceledBy;
	}

	public HoldStatus status() {
		return status;
	}

	public @Nullable HoldCanceledBy canceledBy() {
		return canceledBy;
	}

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
