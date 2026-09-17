package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.Product;
import com.swyp.backend.recipe.dto.IngredientTagResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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
		List<IngredientTagResponse> ingredientTags,
		String status,
		@Nullable Instant reconfirmSentAt,
		@Nullable Instant reconfirmAnsweredAt,
		boolean reconfirmPending,
		int minAdjustableQty,
		boolean stockEditable,
		long activeHoldQty,
		int shortfallQty,
		int stockQty,
		Instant createdAt) {

	public static ProductDetailResponse from(
			Product product,
			long completedQty,
			LocalDateTime now,
			List<IngredientTagResponse> ingredientTags) {
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
				ingredientTags,
				product.getStatus().name(),
				product.getReconfirmSentAt(),
				product.getReconfirmAnsweredAt(),
				product.isStockReconfirmPending(),
				product.minAdjustableQty(),
				product.isStockEditableAt(now),
				product.getHeldQty(),
				product.shortfallQty(),
				product.getStockQty(),
				product.getCreatedAt());
	}
}
