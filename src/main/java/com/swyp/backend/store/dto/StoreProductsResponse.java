package com.swyp.backend.store.dto;

import com.swyp.backend.product.dto.NearbyProductResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
		List<NearbyProductResponse> products) {}
