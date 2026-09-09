package com.swyp.backend.store.dto;

import java.math.BigDecimal;

public record NearbyStoreMarkerResponse(
		Long storeId,
		String name,
		BigDecimal latitude,
		BigDecimal longitude,
		int sellableProductCount) {}
