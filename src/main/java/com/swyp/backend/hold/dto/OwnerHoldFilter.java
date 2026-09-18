package com.swyp.backend.hold.dto;

import com.swyp.backend.hold.entity.HoldCanceledBy;
import com.swyp.backend.hold.entity.HoldStatus;
import org.jspecify.annotations.Nullable;

public enum OwnerHoldFilter {
	HOLDING(HoldStatus.HOLDING, null),
	COMPLETED(HoldStatus.COMPLETED, null),
	EXPIRED(HoldStatus.EXPIRED, null),
	CANCELED(HoldStatus.CANCELED, null),
	CANCELED_BY_OWNER(HoldStatus.CANCELED, HoldCanceledBy.OWNER),
	CANCELED_BY_USER(HoldStatus.CANCELED, HoldCanceledBy.USER);

	private final HoldStatus status;
	private final HoldCanceledBy canceledBy;

	OwnerHoldFilter(HoldStatus status, @Nullable HoldCanceledBy canceledBy) {
		this.status = status;
		this.canceledBy = canceledBy;
	}

	public HoldStatus status() {
		return status;
	}

	public @Nullable HoldCanceledBy canceledBy() {
		return canceledBy;
	}
}
