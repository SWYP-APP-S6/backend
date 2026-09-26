package com.swyp.backend.analytics.dto;

import com.swyp.backend.analytics.entity.DomainEvent;
import com.swyp.backend.analytics.entity.DomainEventType;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record DomainEventResponse(
		Long id,
		DomainEventType eventType,
		@Nullable Long userId,
		@Nullable Long storeId,
		@Nullable Long productId,
		@Nullable Long holdId,
		@Nullable Long recipeId,
		Map<String, Object> payload,
		Instant createdAt) {

	public static DomainEventResponse from(DomainEvent event) {
		return new DomainEventResponse(
				event.getId(),
				event.getEventType(),
				event.getUserId(),
				event.getStoreId(),
				event.getProductId(),
				event.getHoldId(),
				event.getRecipeId(),
				event.getPayload(),
				event.getCreatedAt());
	}
}
