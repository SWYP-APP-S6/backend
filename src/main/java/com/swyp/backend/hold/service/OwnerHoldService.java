package com.swyp.backend.hold.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.hold.dto.OwnerHoldDetailResponse;
import com.swyp.backend.hold.dto.OwnerHoldStatus;
import com.swyp.backend.hold.dto.OwnerHoldSummaryResponse;
import com.swyp.backend.hold.HoldProperties;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldItem;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.exception.HoldErrorCode;
import com.swyp.backend.hold.function.HoldCancelCreditFunction;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.function.NotificationFunction;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.function.ProductFunction;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.function.StoreFunction;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
	private final HoldCancelCreditFunction holdCancelCreditFunction;
	private final HoldProperties holdProperties;
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

	private static void requireOwnedBy(Hold hold, Store store) {
		if (!hold.getStore().getId().equals(store.getId())) {
			throw new BusinessException(HoldErrorCode.HOLD_NOT_FOUND);
		}
	}

	@Transactional
	public OwnerHoldDetailResponse completePickup(Long ownerId, Long holdId) {
		Store store = storeFunction.getByOwnerId(ownerId);
		if (!holdFunction.getStoreIdOfHold(holdId).equals(store.getId())) {
			throw new BusinessException(HoldErrorCode.HOLD_NOT_FOUND);
		}
		Map<Long, Product> locked = new LinkedHashMap<>();
		holdFunction.getProductIdsOfHold(holdId).stream().distinct().sorted()
				.forEach(id -> locked.put(id, productFunction.getByIdForUpdate(id)));

		Hold hold = holdFunction.getByIdForUpdate(holdId);
		Instant now = Instant.now(clock);
		boolean late = requireCompletable(hold, now);

		for (HoldItem item : hold.getItems()) {
			Product product = locked.get(item.getProduct().getId());
			if (late) {
				requireStockLeft(product, item.getQty());
				product.takeFromAvailable(item.getQty());
			} else {
				product.completeHold(item.getQty());
			}
		}
		hold.complete(now);
		if (hold.wasChargedAsNoShow()) {
			holdCancelCreditFunction
					.getOrStart(hold.getUser(), holdProperties.cancelCreditMax(), now)
					.giveBack(holdProperties.cancelCreditMax());
		}
		notificationFunction.notify(
				hold.getUser(),
				NotificationType.PICKUP_COMPLETED,
				"수령이 완료됐어요",
				hold.getStore().getName() + " 수령이 완료됐어요.",
				null);
		return OwnerHoldDetailResponse.from(hold, now);
	}

	private boolean requireCompletable(Hold hold, Instant now) {
		if (hold.getStatus() == HoldStatus.HOLDING) {
			return false;
		}
		if (hold.getStatus() == HoldStatus.EXPIRED
				&& now.isBefore(hold.getExpiresAt().plus(holdProperties.noShowGrace()))) {
			return true;
		}
		throw new BusinessException(HoldErrorCode.HOLD_ALREADY_RESOLVED);
	}

	private static void requireStockLeft(Product product, int qty) {
		if (product.getAvailableQty() < qty) {
			throw new BusinessException(HoldErrorCode.PRODUCT_STOCK_GONE);
		}
	}
}
