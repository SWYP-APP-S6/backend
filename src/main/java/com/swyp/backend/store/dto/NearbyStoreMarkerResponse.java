package com.swyp.backend.store.dto;

import com.swyp.backend.store.entity.Store;
import java.math.BigDecimal;

public record NearbyStoreMarkerResponse(
		Long storeId,
		String name,
		BigDecimal latitude,
		BigDecimal longitude,
		int sellableProductCount) {

	public static NearbyStoreMarkerResponse from(Store store, int sellableProductCount) {
		return new NearbyStoreMarkerResponse(
				store.getId(),
				store.getName(),
				store.getLatitude(),
				store.getLongitude(),
				sellableProductCount);
	}
}
