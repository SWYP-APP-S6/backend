package com.swyp.backend.product.dto;

import com.swyp.backend.common.Distance;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.entity.ProductStatus;
import com.swyp.backend.recipe.dto.RecipeSuggestionResponse;
import com.swyp.backend.store.entity.Store;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.LocalTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ProductBrowseDetailResponse(
		Long id,
		String name,
		ProductCategory category,
		List<String> tags,
		List<String> photoUrls,
		int originalPrice,
		int salePrice,
		short discountRate,
		int availableQty,
		LocalDateTime pickupStartAt,
		LocalDateTime pickupEndAt,
		ProductStatus status,
		HoldButtonState holdButton,
		@Nullable Long myHoldId,
		ProductStore store,
		List<RecipeSuggestionResponse> recipes) {

	public record ProductStore(
			Long id,
			String name,
			String address,
			@Nullable String addressDetail,
			String phone,
			BigDecimal latitude,
			BigDecimal longitude,
			@Nullable Integer distanceMeters,
			@Nullable Integer walkingMinutes,
			LocalTime businessOpenTime,
			LocalTime businessCloseTime,
			boolean openNow) {

		static ProductStore of(
				Store store, @Nullable Integer distanceMeters, boolean openNow) {
			return new ProductStore(
					store.getId(),
					store.getName(),
					store.getAddress(),
					store.getAddressDetail(),
					store.getPhone(),
					store.getLatitude(),
					store.getLongitude(),
					distanceMeters,
					distanceMeters == null
							? null
							: Distance.straightLineWalkingMinutes(distanceMeters),
					store.getBusinessOpenTime(),
					store.getBusinessCloseTime(),
					openNow);
		}
	}

	public static ProductBrowseDetailResponse of(
			Product product,
			List<String> tags,
			@Nullable Long myHoldId,
			boolean holdingAtAnotherStore,
			@Nullable Integer distanceMeters,
			ZonedDateTime serverTime,
			boolean pickupWindowOpen,
			List<RecipeSuggestionResponse> recipes) {
		return new ProductBrowseDetailResponse(
				product.getId(),
				product.getName(),
				product.getCategory(),
				tags,
				List.of(product.getPhotoUrl()),
				product.getOriginalPrice(),
				product.getSalePrice(),
				product.getDiscountRate(),
				product.getAvailableQty(),
				product.getPickupStartAt(),
				product.getPickupEndAt(),
				product.getStatus(),
				buttonStateOf(product, myHoldId, holdingAtAnotherStore, pickupWindowOpen,
						product.getStore().opensOn(serverTime.getDayOfWeek())),
				myHoldId,
				ProductStore.of(
						product.getStore(), distanceMeters, product.getStore().isOpenAt(serverTime)),
				recipes);
	}

	private static HoldButtonState buttonStateOf(Product product, @Nullable Long myHoldId,
			boolean holdingAtAnotherStore, boolean pickupWindowOpen, boolean storeOpensToday) {
		if (myHoldId != null) {
			return HoldButtonState.ALREADY_HOLDING;
		}
		if (product.getStatus() == ProductStatus.CLOSED || !pickupWindowOpen || !storeOpensToday) {
			return HoldButtonState.CLOSED;
		}
		if (product.getStatus() == ProductStatus.SOLD_OUT || product.getAvailableQty() == 0) {
			return HoldButtonState.SOLD_OUT;
		}
		if (holdingAtAnotherStore) {
			return HoldButtonState.OTHER_STORE;
		}
		return HoldButtonState.AVAILABLE;
	}
}
