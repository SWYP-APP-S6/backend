package com.swyp.backend.hold.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.HoldProperties;
import com.swyp.backend.hold.dto.HoldCreateRequest;
import com.swyp.backend.hold.dto.HoldDetailResponse;
import com.swyp.backend.hold.dto.HoldRef;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCancelCredit;
import com.swyp.backend.hold.exception.HoldErrorCode;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.notification.DeepLinks;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.function.NotificationFunction;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class HoldCreator {

	private final HoldFunction holdFunction;
	private final HoldCancelCreditSettler holdCancelCreditSettler;
	private final NotificationFunction notificationFunction;
	private final ProductFunction productFunction;
	private final UserFunction userFunction;
	private final HoldProperties holdProperties;
	private final Clock clock;

	@Transactional
	public HoldDetailResponse create(Long userId, HoldCreateRequest request) {
		Instant now = Instant.now(clock);

		User user = userFunction.getByIdForUpdate(userId);
		HoldCancelCredit credit = holdCancelCreditSettler.settle(user, now);
		if (credit.isEmpty()) {
			throw new BusinessException(
					HoldErrorCode.CANCEL_LIMIT_EXCEEDED,
					credit.nextRefillAt(
							holdProperties.cancelCreditRefill(), holdProperties.cancelCreditMax()));
		}
		Optional<HoldRef> current = holdFunction.findHoldingRefOf(userId);
		Map<Long, Product> locked = lockProducts(productIdsToLock(current, request.productId()));
		Product product = locked.get(request.productId());

		GroupSlot slot = resolveGroup(current, product, now, locked);
		boolean opensAPickup = current.filter(ref -> ref.expiresAt().isAfter(now)).isEmpty();
		Optional<Hold> existing = holdFunction.findHoldingIdOf(userId, product.getId())
				.map(holdFunction::getByIdForUpdate);
		requireWithinQtyLimit(existing.map(Hold::getQty).orElse(0) + request.qty());
		requireSellable(product, request.qty(), now);
		product.hold(request.qty());
		existing.ifPresentOrElse(
				hold -> hold.addQty(request.qty()),
				() -> holdFunction.save(new Hold(user, product.getStore(), product, request.qty(),
						slot.groupId(), slot.expiresAt())));
		askOwnerToReconfirmStock(product, now);
		holdFunction.flush();
		List<Hold> group = holdFunction.findHoldingOfGroup(slot.groupId());
		if (opensAPickup) {
			notificationFunction.notify(
					product.getStore().getOwner(),
					NotificationType.NEW_HOLD_RECEIVED,
					"새 찜이 들어왔어요",
					user.getNickname() + "님이 " + product.getName() + " 상품을 찜했어요.",
					DeepLinks.ownerHold(holdOf(group, product).getId()));
		}
		return HoldDetailResponse.of(group, now, clock.getZone());
	}

	private record GroupSlot(Long groupId, Instant expiresAt) {}

	private static Hold holdOf(List<Hold> group, Product product) {
		return group.stream()
				.filter(hold -> hold.getProduct().getId().equals(product.getId()))
				.findFirst()
				.orElseThrow();
	}

	private void askOwnerToReconfirmStock(Product product, Instant now) {
		if (!product.needsStockReconfirm(holdFunction.completedQtyOfProduct(product.getId()))) {
			return;
		}
		product.markReconfirmSent(now);
		notificationFunction.notify(
				product.getStore().getOwner(),
				NotificationType.STOCK_RECONFIRM_REQUEST,
				"재고가 맞는지 확인해주세요",
				product.getName() + " 찜과 픽업 완료가 등록 수량의 60%에 닿았어요. 지금 남은 수량을 확인해주세요.",
				DeepLinks.ownerProduct(product.getId()));
	}

	private GroupSlot resolveGroup(Optional<HoldRef> current, Product product, Instant now,
			Map<Long, Product> locked) {
		if (current.isEmpty()) {
			return newGroup(product, now);
		}
		HoldRef ref = current.get();
		if (!ref.expiresAt().isAfter(now)) {
			expireGroup(ref.groupId(), locked);
			return newGroup(product, now);
		}
		if (!ref.storeId().equals(product.getStore().getId())) {
			throw new BusinessException(HoldErrorCode.OTHER_STORE_HOLD_ACTIVE);
		}
		Instant pickupBound = pickupBound(product);
		if (pickupBound.isBefore(ref.expiresAt())) {
			lockHoldingOf(ref.groupId()).forEach(sibling -> sibling.restrictExpiryTo(pickupBound));
			return new GroupSlot(ref.groupId(), pickupBound);
		}
		return new GroupSlot(ref.groupId(), ref.expiresAt());
	}

	private GroupSlot newGroup(Product product, Instant now) {
		return new GroupSlot(holdFunction.nextGroupId(), expiresAt(product, now));
	}

	private List<Hold> lockHoldingOf(Long groupId) {
		return holdFunction.findHoldingIdsOfGroup(groupId).stream()
				.map(holdFunction::getByIdForUpdate)
				.toList();
	}

	private void expireGroup(Long groupId, Map<Long, Product> locked) {
		List<Hold> expiring = lockHoldingOf(groupId);
		if (expiring.isEmpty()) {
			return;
		}
		for (Hold hold : expiring) {
			locked.get(hold.getProduct().getId()).releaseHold(hold.getQty());
			hold.expire();
		}
		holdFunction.flush();
		tellTheVisitExpired(expiring.getFirst());
	}

	private void tellTheVisitExpired(Hold hold) {
		notificationFunction.notify(
				hold.getUser(),
				NotificationType.HOLD_EXPIRED,
				"찜 시간이 끝났어요",
				hold.getStore().getName() + "에서 찜한 상품의 픽업 시간이 지났어요.",
				DeepLinks.consumerHold(hold.getId()));
		notificationFunction.notify(
				hold.getStore().getOwner(),
				NotificationType.HOLD_UNCONFIRMED,
				"수령 확인이 안 된 찜이 있어요",
				hold.getUser().getNickname() + "님의 찜 시간이 지났어요. 이미 수령했다면 수령 완료를 눌러주세요.",
				DeepLinks.ownerHold(hold.getId()));
	}

	private List<Long> productIdsToLock(Optional<HoldRef> current, Long productId) {
		List<Long> ids = new ArrayList<>();
		ids.add(productId);
		current.ifPresent(ref -> ids.addAll(holdFunction.findProductIdsOfGroup(ref.groupId())));
		return ids;
	}

	private Map<Long, Product> lockProducts(List<Long> productIds) {
		Map<Long, Product> locked = new LinkedHashMap<>();
		productIds.stream().distinct().sorted()
				.forEach(id -> locked.put(id, productFunction.getByIdForUpdate(id)));
		return locked;
	}

	private void requireWithinQtyLimit(int qty) {
		if (qty > holdProperties.userQtyLimit()) {
			throw new BusinessException(HoldErrorCode.HOLD_LIMIT_EXCEEDED);
		}
	}

	private void requireSellable(Product product, int qty, Instant now) {
		if (product.getStore().getStatus() != StoreStatus.APPROVED) {
			throw new BusinessException(HoldErrorCode.PRODUCT_NOT_SELLABLE);
		}
		if (!product.getStore().opensOn(now.atZone(clock.getZone()).getDayOfWeek())) {
			throw new BusinessException(HoldErrorCode.STORE_CLOSED_TODAY);
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
