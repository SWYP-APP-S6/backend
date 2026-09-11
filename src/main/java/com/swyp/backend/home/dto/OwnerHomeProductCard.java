package com.swyp.backend.home.dto;

import com.swyp.backend.product.entity.Product;

public record OwnerHomeProductCard(
		Long id,
		String name,
		String category,
		String photoUrl,
		int salePrice,
		int availableQty,
		long activeHoldQty,
		long oversoldQty,
		String status,
		boolean reconfirmPending) {

	public static OwnerHomeProductCard from(Product product, long activeHoldQty) {
		return new OwnerHomeProductCard(
				product.getId(),
				product.getName(),
				product.getCategory().name(),
				product.getPhotoUrl(),
				product.getSalePrice(),
				product.getAvailableQty(),
				activeHoldQty,
				Math.max(0L, activeHoldQty - product.getAvailableQty()),
				product.getStatus().name(),
				product.getReconfirmSentAt() != null && product.getReconfirmAnsweredAt() == null);
	}
}
