package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.Product;
import com.swyp.backend.store.entity.Store;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record SellableStoreGroup(Store store, List<Product> products, int distanceMeters) {

	public LocalDateTime earliestPickupEndAt() {
		return products.stream()
				.map(Product::getPickupEndAt)
				.min(Comparator.naturalOrder())
				.orElseThrow();
	}
}
