package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.entity.ProductStatus;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record AdminProductResponse(
		Long id,
		String name,
		ProductCategory category,
		String photoUrl,
		AdminProductStore store,
		int initialQty,
		int stockQty,
		int heldQty,
		int availableQty,
		int originalPrice,
		int salePrice,
		short discountRate,
		LocalDateTime pickupStartAt,
		LocalDateTime pickupEndAt,
		ProductStatus status,
		boolean reconfirmPending,
		@Nullable Instant stockConfirmedAt,
		boolean visibleToConsumers,
		List<VisibilityIssue> hiddenReasons,
		Instant createdAt) {

	public record AdminProductStore(Long id, String name, StoreStatus status) {
		static AdminProductStore from(Store store) {
			return new AdminProductStore(store.getId(), store.getName(), store.getStatus());
		}
	}

	public static AdminProductResponse from(Product product, LocalDateTime now) {
		ProductVisibility visibility = ProductVisibility.diagnose(product, now);
		return new AdminProductResponse(
				product.getId(),
				product.getName(),
				product.getCategory(),
				product.getPhotoUrl(),
				AdminProductStore.from(product.getStore()),
				product.getInitialQty(),
				product.getStockQty(),
				product.getHeldQty(),
				product.getAvailableQty(),
				product.getOriginalPrice(),
				product.getSalePrice(),
				product.getDiscountRate(),
				product.getPickupStartAt(),
				product.getPickupEndAt(),
				product.statusAt(now),
				product.isStockReconfirmPending(),
				product.getStockConfirmedAt(),
				visibility.visibleToConsumers(),
				visibility.hiddenReasons(),
				product.getCreatedAt());
	}
}
