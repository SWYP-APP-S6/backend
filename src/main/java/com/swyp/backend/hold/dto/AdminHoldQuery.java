package com.swyp.backend.hold.dto;

import com.swyp.backend.hold.entity.HoldStatus;
import org.jspecify.annotations.Nullable;

public record AdminHoldQuery(
		@Nullable Long storeId,
		@Nullable Long productId,
		@Nullable Long userId,
		@Nullable HoldStatus status) {
}
