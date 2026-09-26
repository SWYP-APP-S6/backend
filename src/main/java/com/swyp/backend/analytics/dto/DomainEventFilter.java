package com.swyp.backend.analytics.dto;

import com.swyp.backend.analytics.entity.DomainEventType;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record DomainEventFilter(
		@Nullable DomainEventType type,
		@Nullable Long userId,
		@Nullable Long storeId,
		@Nullable Long productId,
		@Nullable Long holdId,
		@Nullable LocalDate from,
		@Nullable LocalDate to) {
}
