package com.swyp.backend.store.dto;

import com.swyp.backend.common.Distance;
import com.swyp.backend.product.dto.SellableProductResponse;
import com.swyp.backend.store.entity.Store;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

public record StoreProductsResponse(
		Long storeId,
		String name,
		BigDecimal latitude,
		BigDecimal longitude,
		Integer distanceMeters,
		Integer walkingMinutes,
		LocalTime businessCloseTime,
		LocalDateTime earliestPickupEndAt,
		int productCount,
		List<SellableProductResponse> products) {

	public static StoreProductsResponse from(
			Store store, List<SellableProductResponse> products, Integer distanceMeters) {
		return new StoreProductsResponse(
				store.getId(),
				store.getName(),
				store.getLatitude(),
				store.getLongitude(),
				distanceMeters,
				distanceMeters == null ? null : Distance.straightLineWalkingMinutes(distanceMeters),
				store.getBusinessCloseTime(),
				products.stream()
						.map(SellableProductResponse::pickupEndAt)
						.min(Comparator.naturalOrder())
						.orElse(null),
				products.size(),
				products);
	}
}
