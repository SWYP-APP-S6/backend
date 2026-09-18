package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.Product;
import com.swyp.backend.recipe.dto.IngredientTagResponse;
import java.time.LocalDateTime;
import java.util.List;

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
		List<IngredientTagResponse> ingredientTags) {

	public static ProductPreviewResponse from(
			Product product, List<IngredientTagResponse> ingredientTags) {
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
				ingredientTags);
	}
}
