package com.swyp.backend.hold.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.HoldProperties;
import com.swyp.backend.hold.dto.HoldCreateRequest;
import com.swyp.backend.hold.dto.HoldDetailResponse;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.exception.HoldErrorCode;
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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HoldService {

	private final HoldFunction holdFunction;
	private final ProductFunction productFunction;
	private final UserFunction userFunction;
	private final HoldProperties holdProperties;
	private final Clock clock;

	@Transactional
	public HoldDetailResponse create(Long userId, HoldCreateRequest request) {
		if (request.qty() > holdProperties.userQtyLimit()) {
			throw new BusinessException(HoldErrorCode.HOLD_LIMIT_EXCEEDED);
		}
		Product product = productFunction.getByIdForUpdate(request.productId());
		Instant now = Instant.now(clock);

		releaseIfAlreadyOverdue(userId, product, now);
		requireSellable(product, request.qty());

		product.hold(request.qty());
		User user = userFunction.getById(userId);
		Hold hold = holdFunction.save(
				new Hold(user, product, request.qty(), expiresAt(product, now)));
		return HoldDetailResponse.from(hold, now);
	}

	@Transactional
	public HoldDetailResponse cancel(Long userId, Long holdId) {
		Long productId = holdFunction.getProductIdOfUserHold(holdId, userId);
		Product product = productFunction.getByIdForUpdate(productId);
		Hold hold = holdFunction.getByIdForUpdate(holdId);
		Instant now = Instant.now(clock);

		requireCancelable(hold, now);

		hold.cancelByUser(now);
		product.releaseHold(hold.getQty());
		return HoldDetailResponse.from(hold, now);
	}

	private static void requireCancelable(Hold hold, Instant now) {
		if (hold.getStatus() != HoldStatus.HOLDING || !hold.getExpiresAt().isAfter(now)) {
			throw new BusinessException(HoldErrorCode.HOLD_ALREADY_RESOLVED);
		}
	}

	private void releaseIfAlreadyOverdue(Long userId, Product product, Instant now) {
		Optional<Hold> holding = holdFunction.findHoldingOf(userId, product.getId());
		if (holding.isEmpty()) {
			return;
		}
		Hold existing = holding.get();
		if (existing.getExpiresAt().isAfter(now)) {
			throw new BusinessException(HoldErrorCode.ALREADY_HOLDING);
		}
		existing.expire();
		product.releaseHold(existing.getQty());
		holdFunction.flush();
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

	private Instant expiresAt(Product product, Instant now) {
		Instant ttlBound = now.plus(holdProperties.ttl());
		Instant pickupBound = product.getPickupEndAt().atZone(clock.getZone()).toInstant();
		return ttlBound.isBefore(pickupBound) ? ttlBound : pickupBound;
	}
}
