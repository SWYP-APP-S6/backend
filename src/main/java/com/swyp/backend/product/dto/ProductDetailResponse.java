package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.Product;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record ProductDetailResponse(
		Long id,
		String name,
		String category,
		int initialQty,
		int availableQty,
		int heldQty,
		long completedQty,
		int originalPrice,
		int salePrice,
		short discountRate,
		LocalDateTime pickupStartAt,
		LocalDateTime pickupEndAt,
		String photoUrl,
		Set<Integer> ingredientTags,
		String status,
		@Nullable Instant reconfirmSentAt,
		@Nullable Instant reconfirmAnsweredAt,
		/** 재고 재확인 모달을 띄워야 하는지. 보낸 뒤 아직 답하지 않은 상태. */
		boolean reconfirmPending,
		/** 수량 스테퍼의 하한. 재확인 전에는 최초 등록의 60%, 그 뒤로는 0. */
		int minAdjustableQty,
		/** 「네, 맞아요」로 확정한 상품은 픽업 마감까지 수량을 고칠 수 없다. */
		boolean stockEditable,
		long activeHoldQty,
		/** 찜이 매장 실제 수량을 넘는 만큼. 선착순 취소 대상 수량이다. */
		int shortfallQty,
		/** 매장에 실제로 있는 총 수량(찜 포함). 점주가 O-030 에서 적는 값이다. */
		int stockQty,
		Instant createdAt) {

	public static ProductDetailResponse from(Product product, long completedQty) {
		return from(product, completedQty, product.getHeldQty());
	}

	public static ProductDetailResponse from(
			Product product, long completedQty, long activeHoldQty) {
		return new ProductDetailResponse(
				product.getId(),
				product.getName(),
				product.getCategory().name(),
				product.getInitialQty(),
				product.getAvailableQty(),
				product.getHeldQty(),
				completedQty,
				product.getOriginalPrice(),
				product.getSalePrice(),
				product.getDiscountRate(),
				product.getPickupStartAt(),
				product.getPickupEndAt(),
				product.getPhotoUrl(),
				product.getIngredientIds(),
				product.getStatus().name(),
				product.getReconfirmSentAt(),
				product.getReconfirmAnsweredAt(),
				product.isStockReconfirmPending(),
				product.minAdjustableQty(),
				!product.isStockLocked(),
				activeHoldQty,
				product.shortfallQty(),
				product.getStockQty(),
				product.getCreatedAt());
	}
}
