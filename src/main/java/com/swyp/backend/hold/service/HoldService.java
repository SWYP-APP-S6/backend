package com.swyp.backend.hold.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.HoldProperties;
import com.swyp.backend.hold.dto.ActiveHoldResponse;
import com.swyp.backend.hold.dto.HoldCreateRequest;
import com.swyp.backend.hold.dto.HoldDetailResponse;
import com.swyp.backend.hold.dto.HoldRef;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldItem;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.exception.HoldErrorCode;
import com.swyp.backend.hold.entity.HoldCancelCredit;
import com.swyp.backend.hold.function.HoldCancelCreditFunction;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductStatus;
import com.swyp.backend.product.function.ProductFunction;
import com.swyp.backend.store.entity.StoreStatus;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.function.UserFunction;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HoldService {

	private final HoldFunction holdFunction;
	private final HoldCancelCreditFunction holdCancelCreditFunction;
	private final ProductFunction productFunction;
	private final UserFunction userFunction;
	private final HoldProperties holdProperties;
	private final Clock clock;

	@Transactional
	public HoldDetailResponse create(Long userId, HoldCreateRequest request) {
		Instant now = Instant.now(clock);

		User user = userFunction.getByIdForUpdate(userId);
		HoldCancelCredit credit = settleCredits(user, now);
		if (credit.isEmpty()) {
			throw new BusinessException(
					HoldErrorCode.CANCEL_LIMIT_EXCEEDED,
					credit.nextRefillAt(
							holdProperties.cancelCreditRefill(), holdProperties.cancelCreditMax()));
		}
		Optional<HoldRef> current = holdFunction.findHoldingRefOf(userId);
		Map<Long, Product> locked = lockProducts(productIdsToLock(current, request.productId(), now));
		Product product = locked.get(request.productId());

		Hold hold = resolveHold(user, current, product, now, locked);
		requireWithinQtyLimit(hold, product, request.qty());
		requireSellable(product, request.qty());
		product.hold(request.qty());
		hold.addItem(product, request.qty());
		hold.restrictExpiryTo(pickupBound(product));
		return HoldDetailResponse.from(hold, now);
	}

	@Transactional
	public HoldDetailResponse cancel(Long userId, Long holdId) {
		List<Long> productIds = holdFunction.getProductIdsOfUserHold(userId, holdId);
		User user = userFunction.getByIdForUpdate(userId);
		Map<Long, Product> locked = lockProducts(productIds);
		Hold hold = holdFunction.getByIdForUpdate(holdId);
		Instant now = Instant.now(clock);

		requireCancelable(hold, now);

		releaseAll(hold, locked);
		hold.cancelByUser(now);
		chargeUnlessMisTap(hold, user, now);
		return HoldDetailResponse.from(hold, now);
	}

	private void chargeUnlessMisTap(Hold hold, User user, Instant now) {
		if (hold.getCreatedAt().plus(holdProperties.freeCancelWindow()).isAfter(now)) {
			return;
		}
		settleCredits(user, now).spend(1);
	}

	public HoldDetailResponse getHold(Long userId, Long holdId) {
		Instant now = Instant.now(clock);
		return HoldDetailResponse.from(holdFunction.getDetailOfUserHold(userId, holdId), now);
	}

	public ActiveHoldResponse getActiveHold(Long userId) {
		Instant now = Instant.now(clock);
		HoldCancelCredit credit = creditsAsOf(userId, now);
		return new ActiveHoldResponse(
				holdFunction.findActiveOf(userId, now)
						.map(hold -> HoldDetailResponse.from(hold, now))
						.orElse(null),
				credit.getCredits(),
				credit.nextRefillAt(
						holdProperties.cancelCreditRefill(), holdProperties.cancelCreditMax()));
	}

	private HoldCancelCredit creditsAsOf(Long userId, Instant now) {
		HoldCancelCredit credit = holdCancelCreditFunction.findOf(userId)
				.orElseGet(() -> new HoldCancelCredit(
						null, holdProperties.cancelCreditMax(), now));
		credit.refill(now, holdProperties.cancelCreditRefill(), holdProperties.cancelCreditMax());
		credit.spend(holdFunction
				.findUnchargedNoShows(userId, now.minus(holdProperties.noShowGrace()))
				.size());
		return credit;
	}

	private Hold resolveHold(User user, Optional<HoldRef> current, Product product, Instant now,
			Map<Long, Product> locked) {
		if (current.isEmpty()) {
			return newHold(user, product, now);
		}
		Hold existing = holdFunction.getByIdForUpdate(current.get().holdId());
		if (existing.getStatus() != HoldStatus.HOLDING) {
			return newHold(user, product, now);
		}
		if (existing.isOverdueAt(now)) {
			releaseAll(existing, locked);
			existing.expire();
			holdFunction.flush();
			return newHold(user, product, now);
		}
		if (!existing.getStore().getId().equals(product.getStore().getId())) {
			throw new BusinessException(HoldErrorCode.OTHER_STORE_HOLD_ACTIVE);
		}
		return existing;
	}

	private Hold newHold(User user, Product product, Instant now) {
		return holdFunction.save(new Hold(user, product.getStore(), expiresAt(product, now)));
	}

	private List<Long> productIdsToLock(
			Optional<HoldRef> current, Long productId, Instant now) {
		List<Long> ids = new ArrayList<>();
		ids.add(productId);
		current.filter(ref -> !ref.expiresAt().isAfter(now))
				.ifPresent(ref -> ids.addAll(holdFunction.getProductIdsOfHold(ref.holdId())));
		return ids;
	}

	private Map<Long, Product> lockProducts(List<Long> productIds) {
		Map<Long, Product> locked = new LinkedHashMap<>();
		productIds.stream().distinct().sorted()
				.forEach(id -> locked.put(id, productFunction.getByIdForUpdate(id)));
		return locked;
	}

	private void releaseAll(Hold hold, Map<Long, Product> locked) {
		for (HoldItem item : hold.getItems()) {
			locked.get(item.getProduct().getId()).releaseHold(item.getQty());
		}
	}

	private void requireWithinQtyLimit(Hold hold, Product product, int qty) {
		int held = hold.itemOf(product.getId()).map(HoldItem::getQty).orElse(0);
		if (held + qty > holdProperties.userQtyLimit()) {
			throw new BusinessException(HoldErrorCode.HOLD_LIMIT_EXCEEDED);
		}
	}

	private HoldCancelCredit settleCredits(User user, Instant now) {
		HoldCancelCredit credit = holdCancelCreditFunction.getOrStart(
				user, holdProperties.cancelCreditMax(), now);
		credit.refill(now, holdProperties.cancelCreditRefill(), holdProperties.cancelCreditMax());

		List<Hold> noShows = holdFunction.findUnchargedNoShows(
				user.getId(), now.minus(holdProperties.noShowGrace()));
		noShows.forEach(noShow -> noShow.markNoShowCharged(now));
		credit.spend(noShows.size());
		return credit;
	}

	private static void requireCancelable(Hold hold, Instant now) {
		if (hold.getStatus() != HoldStatus.HOLDING) {
			throw new BusinessException(HoldErrorCode.HOLD_ALREADY_RESOLVED);
		}
		if (hold.isOverdueAt(now)) {
			throw new BusinessException(HoldErrorCode.HOLD_ALREADY_EXPIRED);
		}
	}

	private void requireSellable(Product product, int qty) {
		if (product.getStore().getStatus() != StoreStatus.APPROVED) {
			throw new BusinessException(HoldErrorCode.PRODUCT_NOT_SELLABLE);
		}
		if (product.getStatus() != ProductStatus.ON_SALE) {
			throw new BusinessException(HoldErrorCode.PRODUCT_NOT_SELLABLE);
		}
		if (!product.getPickupEndAt().isAfter(LocalDateTime.now(clock))) {
			throw new BusinessException(HoldErrorCode.PRODUCT_NOT_SELLABLE);
		}
		if (qty > product.getAvailableQty()) {
			throw new BusinessException(HoldErrorCode.INSUFFICIENT_QTY);
		}
	}

	private Instant pickupBound(Product product) {
		return product.getPickupEndAt().atZone(clock.getZone()).toInstant();
	}

	private Instant expiresAt(Product product, Instant now) {
		Instant ttlBound = now.plus(holdProperties.ttl());
		Instant pickupBound = pickupBound(product);
		return ttlBound.isBefore(pickupBound) ? ttlBound : pickupBound;
	}
}
