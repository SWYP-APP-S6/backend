package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import java.time.LocalDateTime;

public record SellableProductResponse(
		Long id,
		String name,
		String photoUrl,
		ProductCategory category,
		int originalPrice,
		int salePrice,
		short discountRate,
		int availableQty,
		LocalDateTime pickupEndAt) {

	public static SellableProductResponse from(Product product) {
		return new SellableProductResponse(
				product.getId(),
				product.getName(),
				product.getPhotoUrl(),
				product.getCategory(),
				product.getOriginalPrice(),
				product.getSalePrice(),
				product.getDiscountRate(),
				product.getAvailableQty(),
				product.getPickupEndAt());
	}
}
