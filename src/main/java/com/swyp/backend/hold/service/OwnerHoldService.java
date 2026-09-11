package com.swyp.backend.hold.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.hold.dto.HoldTarget;
import com.swyp.backend.hold.dto.OwnerHoldDetailResponse;
import com.swyp.backend.hold.dto.OwnerHoldStatus;
import com.swyp.backend.hold.dto.OwnerHoldSummaryResponse;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.exception.HoldErrorCode;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.function.NotificationFunction;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.function.ProductFunction;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.function.StoreFunction;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnerHoldService {

	private final HoldFunction holdFunction;
	private final StoreFunction storeFunction;
	private final ProductFunction productFunction;
	private final NotificationFunction notificationFunction;
	private final Clock clock;

	public PageResponse<OwnerHoldSummaryResponse> getHolds(
			Long ownerId, OwnerHoldStatus filter, Pageable pageable) {
		Store store = storeFunction.getByOwnerId(ownerId);
		Page<Hold> holds = holdFunction.findStoreHolds(store.getId(), filter, pageable);
		List<OwnerHoldSummaryResponse> content = holds.getContent().stream()
				.map(OwnerHoldSummaryResponse::from)
				.toList();
		return PageResponse.of(content, holds);
	}

	public OwnerHoldDetailResponse getHold(Long ownerId, Long holdId) {
		Store store = storeFunction.getByOwnerId(ownerId);
		Hold hold = holdFunction.getDetailById(holdId);
		requireOwnedBy(hold, store);
		return OwnerHoldDetailResponse.from(hold, Instant.now(clock));
	}

	@Transactional
	public OwnerHoldDetailResponse completePickup(Long ownerId, Long holdId) {
		Store store = storeFunction.getByOwnerId(ownerId);
		HoldTarget target = holdFunction.getTargetById(holdId);
		if (!target.storeId().equals(store.getId())) {
			throw new BusinessException(HoldErrorCode.HOLD_NOT_FOUND);
		}

		Product product = productFunction.getByIdForUpdate(target.productId());
		Hold hold = holdFunction.getByIdForUpdate(holdId);
		if (hold.getStatus() != HoldStatus.HOLDING) {
			throw new BusinessException(HoldErrorCode.HOLD_ALREADY_RESOLVED);
		}

		Instant now = Instant.now(clock);
		hold.complete(now);
		product.completeHold(hold.getQty());
		notificationFunction.notify(
				hold.getUser(),
				NotificationType.PICKUP_COMPLETED,
				"수령이 완료됐어요",
				product.getName() + " 수령이 완료됐어요.",
				null);
		return OwnerHoldDetailResponse.from(hold, now);
	}

	private static void requireOwnedBy(Hold hold, Store store) {
		if (!hold.getProduct().getStore().getId().equals(store.getId())) {
			throw new BusinessException(HoldErrorCode.HOLD_NOT_FOUND);
		}
	}
}
