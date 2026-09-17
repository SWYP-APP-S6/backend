package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.Product;
import java.time.Instant;
import java.time.LocalDateTime;

public record OwnerProductSummaryResponse(
		Long id,
		String name,
		String category,
		String photoUrl,
		int initialQty,
		int availableQty,
		long activeHoldQty,
		int shortfallQty,
		int originalPrice,
		int salePrice,
		short discountRate,
		LocalDateTime pickupEndAt,
		String status,
		boolean reconfirmPending,
		Instant createdAt) {

	public static OwnerProductSummaryResponse from(Product product, long activeHoldQty) {
		return new OwnerProductSummaryResponse(
				product.getId(),
				product.getName(),
				product.getCategory().name(),
				product.getPhotoUrl(),
				product.getInitialQty(),
				product.getAvailableQty(),
				activeHoldQty,
				product.shortfallQty(),
				product.getOriginalPrice(),
				product.getSalePrice(),
				product.getDiscountRate(),
				product.getPickupEndAt(),
				product.getStatus().name(),
				product.isStockReconfirmPending(),
				product.getCreatedAt());
	}
}
