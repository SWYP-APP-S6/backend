package com.swyp.backend.hold.dto;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCanceledBy;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductStatus;
import com.swyp.backend.store.entity.Store;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

public record HoldDetailResponse(
		Long id,
		HoldStatus status,
		int qty,
		int unitPrice,
		int totalPrice,
		Instant heldAt,
		Instant expiresAt,
		Instant serverTime,
		@Nullable Instant completedAt,
		@Nullable Instant canceledAt,
		@Nullable HoldCanceledBy canceledBy,
		@Nullable String cancelReason,
		HoldProduct product,
		HoldStore store) {

	public record HoldProduct(
			Long id,
			String name,
			String photoUrl,
			int originalPrice,
			int salePrice,
			short discountRate,
			ProductStatus status) {

		static HoldProduct from(Product product) {
			return new HoldProduct(
					product.getId(),
					product.getName(),
					product.getPhotoUrl(),
					product.getOriginalPrice(),
					product.getSalePrice(),
					product.getDiscountRate(),
					product.getStatus());
		}
	}

	public record HoldStore(
			Long id,
			String name,
			String address,
			@Nullable String addressDetail,
			String phone,
			BigDecimal latitude,
			BigDecimal longitude,
			LocalTime businessOpenTime,
			LocalTime businessCloseTime) {

		static HoldStore from(Store store) {
			return new HoldStore(
					store.getId(),
					store.getName(),
					store.getAddress(),
					store.getAddressDetail(),
					store.getPhone(),
					store.getLatitude(),
					store.getLongitude(),
					store.getBusinessOpenTime(),
					store.getBusinessCloseTime());
		}
	}

	private static HoldStatus statusAt(Hold hold, Instant serverTime) {
		if (hold.getStatus() == HoldStatus.HOLDING && !hold.getExpiresAt().isAfter(serverTime)) {
			return HoldStatus.EXPIRED;
		}
		return hold.getStatus();
	}

	public static HoldDetailResponse from(Hold hold, Instant serverTime) {
		Product product = hold.getProduct();
		return new HoldDetailResponse(
				hold.getId(),
				statusAt(hold, serverTime),
				hold.getQty(),
				product.getSalePrice(),
				product.getSalePrice() * hold.getQty(),
				hold.getCreatedAt(),
				hold.getExpiresAt(),
				serverTime,
				hold.getCompletedAt(),
				hold.getCanceledAt(),
				hold.getCanceledBy(),
				hold.getCancelReason(),
				HoldProduct.from(product),
				HoldStore.from(product.getStore()));
	}
}
