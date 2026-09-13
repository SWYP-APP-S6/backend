package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.Product;
import java.time.LocalDateTime;
import java.util.Set;

public record ProductPreviewResponse(
		String name,
		String category,
		String photoUrl,
		int initialQty,
		int originalPrice,
		int salePrice,
		short discountRate,
		LocalDateTime pickupStartAt,
		LocalDateTime pickupEndAt,
		Set<Integer> ingredientTags) {

	public static ProductPreviewResponse from(Product product) {
		return new ProductPreviewResponse(
				product.getName(),
				product.getCategory().name(),
				product.getPhotoUrl(),
				product.getInitialQty(),
				product.getOriginalPrice(),
				product.getSalePrice(),
				product.getDiscountRate(),
				product.getPickupStartAt(),
				product.getPickupEndAt(),
				product.getIngredientIds());
	}
}
