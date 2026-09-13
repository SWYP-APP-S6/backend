package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.Product;
import com.swyp.backend.store.entity.Store;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record SellableStoreGroup(Store store, List<Product> products, int distanceMeters) {

	/** 할인율순은 매장을 줄 세우는 값이라 그 매장이 내건 가장 큰 할인을 대표로 쓴다. */
	public short bestDiscountRate() {
		return products.stream()
				.map(Product::getDiscountRate)
				.max(Comparator.naturalOrder())
				.orElseThrow();
	}

	public LocalDateTime earliestPickupEndAt() {
		return products.stream()
				.map(Product::getPickupEndAt)
				.min(Comparator.naturalOrder())
				.orElseThrow();
	}
}
