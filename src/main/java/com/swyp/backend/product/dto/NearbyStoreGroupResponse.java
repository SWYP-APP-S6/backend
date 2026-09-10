package com.swyp.backend.product.dto;

import com.swyp.backend.common.Distance;
import com.swyp.backend.store.entity.Store;
import java.time.LocalDateTime;
import java.util.List;

public record NearbyStoreGroupResponse(
		Long storeId,
		String storeName,
		int distanceMeters,
		int walkingMinutes,
		int productCount,
		boolean hasMoreProducts,
		LocalDateTime earliestPickupEndAt,
		List<SellableProductResponse> products) {

	private static final int MAX_INLINE_PRODUCTS = 10;

	public static NearbyStoreGroupResponse from(SellableStoreGroup group) {
		Store store = group.store();
		List<SellableProductResponse> visible = group.products().stream()
				.limit(MAX_INLINE_PRODUCTS)
				.map(SellableProductResponse::from)
				.toList();
		return new NearbyStoreGroupResponse(
				store.getId(),
				store.getName(),
				group.distanceMeters(),
				Distance.straightLineWalkingMinutes(group.distanceMeters()),
				group.products().size(),
				group.products().size() > visible.size(),
				group.earliestPickupEndAt(),
				visible);
	}
}
