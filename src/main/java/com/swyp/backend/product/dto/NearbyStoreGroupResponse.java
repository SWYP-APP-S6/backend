package com.swyp.backend.product.dto;

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
		List<SellableProductResponse> products) {}
