package com.swyp.backend.hold.dto;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCanceledBy;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.product.entity.Product;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record HoldSummaryResponse(
		Long id,
		HoldStatus status,
		Long storeId,
		String storeName,
		Long productId,
		String productName,
		String photoUrl,
		int qty,
		int totalPrice,
		Instant heldAt,
		Instant expiresAt,
		@Nullable Instant completedAt,
		@Nullable Instant canceledAt,
		@Nullable HoldCanceledBy canceledBy,
		/** 이 찜이 취소권을 실제로 깎았는지. 오조작 유예 안의 취소와 잔액 0 에서의 노쇼는 false. */
		boolean cancelCreditUsed) {

	public static HoldSummaryResponse from(
			Hold hold, Instant serverTime, boolean cancelCreditUsed) {
		Product product = hold.getProduct();
		return new HoldSummaryResponse(
				hold.getId(),
				hold.statusAt(serverTime),
				hold.getStore().getId(),
				hold.getStore().getName(),
				product.getId(),
				product.getName(),
				product.getPhotoUrl(),
				hold.getQty(),
				product.getSalePrice() * hold.getQty(),
				hold.getCreatedAt(),
				hold.getExpiresAt(),
				hold.getCompletedAt(),
				hold.getCanceledAt(),
				hold.getCanceledBy(),
				cancelCreditUsed);
	}
}
