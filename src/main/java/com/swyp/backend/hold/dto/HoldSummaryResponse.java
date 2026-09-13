package com.swyp.backend.hold.dto;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCanceledBy;
import com.swyp.backend.hold.entity.HoldItem;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.product.entity.Product;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record HoldSummaryResponse(
		Long id,
		HoldStatus status,
		Long storeId,
		String storeName,
		int totalQty,
		int totalPrice,
		List<HoldSummaryItem> items,
		Instant heldAt,
		Instant expiresAt,
		@Nullable Instant completedAt,
		@Nullable Instant canceledAt,
		@Nullable HoldCanceledBy canceledBy,
		/** 이 찜이 취소권을 실제로 깎았는지. 오조작 유예 안의 취소와 잔액 0 에서의 노쇼는 false. */
		boolean cancelCreditUsed) {

	public record HoldSummaryItem(
			Long productId, String name, String photoUrl, int qty, int lineTotal) {

		static HoldSummaryItem from(HoldItem item) {
			Product product = item.getProduct();
			return new HoldSummaryItem(
					product.getId(),
					product.getName(),
					product.getPhotoUrl(),
					item.getQty(),
					product.getSalePrice() * item.getQty());
		}
	}

	public static HoldSummaryResponse from(
			Hold hold, Instant serverTime, boolean cancelCreditUsed) {
		List<HoldSummaryItem> items = hold.getItems().stream()
				.sorted(Comparator.comparing(item -> item.getProduct().getId()))
				.map(HoldSummaryItem::from)
				.toList();
		return new HoldSummaryResponse(
				hold.getId(),
				hold.statusAt(serverTime),
				hold.getStore().getId(),
				hold.getStore().getName(),
				items.stream().mapToInt(HoldSummaryItem::qty).sum(),
				items.stream().mapToInt(HoldSummaryItem::lineTotal).sum(),
				items,
				hold.getCreatedAt(),
				hold.getExpiresAt(),
				hold.getCompletedAt(),
				hold.getCanceledAt(),
				hold.getCanceledBy(),
				cancelCreditUsed);
	}
}
