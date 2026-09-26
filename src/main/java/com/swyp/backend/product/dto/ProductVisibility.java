package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductStatus;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record ProductVisibility(boolean visibleToConsumers, List<VisibilityIssue> hiddenReasons) {

	private static final BigDecimal MIN_LATITUDE = new BigDecimal("33");
	private static final BigDecimal MAX_LATITUDE = new BigDecimal("39");
	private static final BigDecimal MIN_LONGITUDE = new BigDecimal("124");
	private static final BigDecimal MAX_LONGITUDE = new BigDecimal("132");

	public static ProductVisibility diagnose(Product product, LocalDateTime now) {
		Store store = product.getStore();
		List<VisibilityIssue> issues = new ArrayList<>();
		if (store.getStatus() != StoreStatus.APPROVED) {
			issues.add(VisibilityIssue.STORE_NOT_APPROVED);
		}
		if (!store.opensOn(now.getDayOfWeek())) {
			issues.add(VisibilityIssue.STORE_CLOSED_TODAY);
		}
		if (!within(store.getLatitude(), MIN_LATITUDE, MAX_LATITUDE)
				|| !within(store.getLongitude(), MIN_LONGITUDE, MAX_LONGITUDE)) {
			issues.add(VisibilityIssue.STORE_LOCATION_OUT_OF_RANGE);
		}
		if (product.getStatus() == ProductStatus.CLOSED) {
			issues.add(VisibilityIssue.PRODUCT_CLOSED);
		}
		if (!product.getPickupEndAt().isAfter(now)) {
			issues.add(VisibilityIssue.PICKUP_ENDED);
		}
		if (product.getAvailableQty() < 1) {
			issues.add(VisibilityIssue.NO_STOCK);
		}
		return new ProductVisibility(issues.isEmpty(), List.copyOf(issues));
	}

	private static boolean within(BigDecimal value, BigDecimal min, BigDecimal max) {
		return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
	}
}
