package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.Product;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record ProductDetailResponse(
		Long id,
		String name,
		String category,
		int initialQty,
		int availableQty,
		int heldQty,
		long completedQty,
		int originalPrice,
		int salePrice,
		short discountRate,
		LocalDateTime pickupStartAt,
		LocalDateTime pickupEndAt,
		String photoUrl,
		Set<Integer> ingredientTags,
		String status,
		@Nullable Instant reconfirmSentAt,
		@Nullable Instant reconfirmAnsweredAt,
		boolean reconfirmPending,
		boolean stockEditable,
		long activeHoldQty,
		int shortfallQty,
		int stockQty,
		Instant createdAt) {

	public static ProductDetailResponse from(
			Product product, long completedQty, LocalDateTime now) {
		return new ProductDetailResponse(
				product.getId(),
				product.getName(),
				product.getCategory().name(),
				product.getInitialQty(),
				product.getAvailableQty(),
				product.getHeldQty(),
				completedQty,
				product.getOriginalPrice(),
				product.getSalePrice(),
				product.getDiscountRate(),
				product.getPickupStartAt(),
				product.getPickupEndAt(),
				product.getPhotoUrl(),
				product.getIngredientIds(),
				product.statusAt(now).name(),
				product.getReconfirmSentAt(),
				product.getReconfirmAnsweredAt(),
				product.isStockReconfirmPending(),
				product.isStockEditableAt(now),
				product.getHeldQty(),
				product.shortfallQty(),
				product.getStockQty(),
				product.getCreatedAt());
	}
}
