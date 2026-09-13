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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record HoldDetailResponse(
		Long id,
		Long groupId,
		HoldStatus status,
		int totalQty,
		int totalPrice,
		Instant heldAt,
		Instant expiresAt,
		Instant serverTime,
		@Nullable Instant completedAt,
		@Nullable Instant canceledAt,
		@Nullable HoldCanceledBy canceledBy,
		@Nullable String cancelReason,
		HoldStore store,
		List<HoldItemResponse> items) {

	public record HoldItemResponse(
			Long holdId,
			Long productId,
			String name,
			String photoUrl,
			int originalPrice,
			int salePrice,
			short discountRate,
			ProductStatus status,
			int qty,
			int lineTotal) {

		static HoldItemResponse from(Hold hold) {
			Product product = hold.getProduct();
			return new HoldItemResponse(
					hold.getId(),
					product.getId(),
					product.getName(),
					product.getPhotoUrl(),
					product.getOriginalPrice(),
					product.getSalePrice(),
					product.getDiscountRate(),
					product.getStatus(),
					hold.getQty(),
					product.getSalePrice() * hold.getQty());
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
			LocalTime businessCloseTime,
			boolean openNow) {

		static HoldStore from(Store store, ZonedDateTime serverTime) {
			return new HoldStore(
					store.getId(),
					store.getName(),
					store.getAddress(),
					store.getAddressDetail(),
					store.getPhone(),
					store.getLatitude(),
					store.getLongitude(),
					store.getBusinessOpenTime(),
					store.getBusinessCloseTime(),
					store.isOpenAt(serverTime));
		}
	}

	public static HoldDetailResponse of(List<Hold> group, Instant serverTime, ZoneId zone) {
		List<HoldItemResponse> items = group.stream()
				.sorted(Comparator.comparing(held -> held.getProduct().getId()))
				.map(HoldItemResponse::from)
				.toList();
		Hold hold = group.stream().min(Comparator.comparing(Hold::getId)).orElseThrow();
		return new HoldDetailResponse(
				hold.getId(),
				hold.getGroupId(),
				hold.statusAt(serverTime),
				items.stream().mapToInt(HoldItemResponse::qty).sum(),
				items.stream().mapToInt(HoldItemResponse::lineTotal).sum(),
				hold.getCreatedAt(),
				hold.getExpiresAt(),
				serverTime,
				hold.getCompletedAt(),
				hold.getCanceledAt(),
				hold.getCanceledBy(),
				hold.getCancelReason(),
				HoldStore.from(hold.getStore(), serverTime.atZone(zone)),
				items);
	}
}
