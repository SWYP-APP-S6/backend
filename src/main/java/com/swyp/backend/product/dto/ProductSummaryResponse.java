package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.Product;
import java.time.Instant;

public record ProductSummaryResponse(
		Long id,
		String name,
		String category,
		String photoUrl,
		int initialQty,
		int availableQty,
		String status,
		long activeHoldQty,
		boolean oversold,
		boolean reconfirmPending,
		Instant createdAt) {

	public static ProductSummaryResponse from(Product product, long activeHoldQty) {
		return new ProductSummaryResponse(
				product.getId(),
				product.getName(),
				product.getCategory().name(),
				product.getPhotoUrl(),
				product.getInitialQty(),
				product.getAvailableQty(),
				product.getStatus().name(),
				activeHoldQty,
				product.getAvailableQty() < activeHoldQty,
				product.getReconfirmSentAt() != null && product.getReconfirmAnsweredAt() == null,
				product.getCreatedAt());
	}
}
