package com.swyp.backend.hold.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.hold.dto.OwnerHoldCounts;
import com.swyp.backend.hold.dto.OwnerHoldDetailResponse;
import com.swyp.backend.hold.dto.OwnerHoldListResponse;
import com.swyp.backend.hold.dto.OwnerHoldFilter;
import com.swyp.backend.hold.dto.OwnerHoldSummaryResponse;
import com.swyp.backend.hold.HoldProperties;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCancelCreditReason;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.exception.HoldErrorCode;
import com.swyp.backend.hold.function.HoldCancelCreditFunction;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.notification.DeepLinks;
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

	public OwnerHoldListResponse getHolds(
			Long ownerId, OwnerHoldFilter filter, Pageable pageable) {
		Store store = storeFunction.getByOwnerId(ownerId);
		Page<Hold> holds = holdFunction.findStoreHolds(store.getId(), filter, pageable);
		List<OwnerHoldSummaryResponse> content = holds.getContent().stream()
				.map(OwnerHoldSummaryResponse::from)
				.toList();
		return new OwnerHoldListResponse(
				Instant.now(clock),
				OwnerHoldCounts.from(holdFunction.countStoreHoldsByOwnerStatus(store.getId())),
				PageResponse.of(content, holds));
	}

	public OwnerHoldDetailResponse getHold(Long ownerId, Long holdId) {
		Store store = storeFunction.getByOwnerId(ownerId);
		Hold hold = holdFunction.getDetailById(holdId);
		requireOwnedBy(hold, store);
		List<Hold> group = hold.getStatus() == HoldStatus.HOLDING
				? holdFunction.findHoldingOfGroup(hold.getGroupId())
				: List.of(hold);
		return OwnerHoldDetailResponse.of(group, Instant.now(clock));
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
		List<Long> holdIds = holdFunction.getPickupableHoldIdsOfGroup(holdId);
		Map<Long, Product> locked = new LinkedHashMap<>();
		holdFunction.findProductIdsOfHolds(holdIds).stream().distinct().sorted()
				.forEach(id -> locked.put(id, productFunction.getByIdForUpdate(id)));

		List<Hold> group = holdIds.stream()
				.map(holdFunction::getByIdForUpdate)
				.toList();
		Hold requested = group.stream()
				.filter(hold -> hold.getId().equals(holdId))
				.findFirst()
				.orElseThrow(() -> new BusinessException(HoldErrorCode.HOLD_ALREADY_RESOLVED));

		Instant now = Instant.now(clock);
		requireCompletable(requested, now);

		boolean chargedAsNoShow = false;
		for (Hold hold : group) {
			Product product = locked.get(hold.getProduct().getId());
			if (hold.getStatus() == HoldStatus.EXPIRED) {
				requireStockLeft(product, hold.getQty());
				product.takeFromStock(hold.getQty());
			} else {
				product.completeHold(hold.getQty());
			}
			chargedAsNoShow |= hold.wasChargedAsNoShow();
			hold.complete(now);
		}
		if (chargedAsNoShow) {
			giveBackNoShowCredit(requested, now);
		}
		notificationFunction.notify(
				requested.getUser(),
				NotificationType.PICKUP_COMPLETED,
				"수령이 완료됐어요",
				requested.getStore().getName() + " 수령이 완료됐어요.",
				DeepLinks.consumerHold(requested.getId()));
		return OwnerHoldDetailResponse.of(group, now);
	}

	private void giveBackNoShowCredit(Hold hold, Instant now) {
		int given = holdCancelCreditFunction
				.getOrStart(hold.getUser(), holdProperties.cancelCreditMax(), now)
				.giveBack(holdProperties.cancelCreditMax());
		if (given > 0) {
			holdCancelCreditFunction.record(
					hold.getUser(), hold, HoldCancelCreditReason.GIVE_BACK, given, now);
		}
	}

	private void requireCompletable(Hold hold, Instant now) {
		if (hold.getStatus() == HoldStatus.HOLDING) {
			return;
		}
		if (hold.getStatus() == HoldStatus.EXPIRED
				&& now.isBefore(hold.getExpiresAt().plus(holdProperties.noShowGrace()))) {
			return;
		}
		throw new BusinessException(HoldErrorCode.HOLD_ALREADY_RESOLVED);
	}

	private static void requireStockLeft(Product product, int qty) {
		if (product.getAvailableQty() < qty) {
			throw new BusinessException(HoldErrorCode.PRODUCT_STOCK_GONE);
		}
	}
}
