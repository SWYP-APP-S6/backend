package com.swyp.backend.hold.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.hold.HoldProperties;
import com.swyp.backend.hold.dto.ActiveHoldResponse;
import com.swyp.backend.hold.dto.HoldCreateRequest;
import com.swyp.backend.hold.dto.HoldDetailResponse;
import com.swyp.backend.hold.dto.HoldHistoryResponse;
import com.swyp.backend.hold.dto.HoldRef;
import com.swyp.backend.hold.dto.HoldSummaryResponse;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.exception.HoldErrorCode;
import com.swyp.backend.hold.entity.HoldCancelCredit;
import com.swyp.backend.hold.entity.HoldCancelCreditReason;
import com.swyp.backend.hold.function.HoldCancelCreditFunction;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HoldService {

	private final HoldFunction holdFunction;
	private final HoldCancelCreditFunction holdCancelCreditFunction;
	private final NotificationFunction notificationFunction;
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
		if (!product.needsStockReconfirm()) {
			return;
		}
		product.markReconfirmSent(now);
		notificationFunction.notify(
				product.getStore().getOwner(),
				NotificationType.STOCK_RECONFIRM_REQUEST,
				"재고가 맞는지 확인해주세요",
				product.getName() + " 찜이 등록 수량의 60%에 닿았어요. 지금 남은 수량을 확인해주세요.",
				DeepLinks.ownerProduct(product.getId()));
	}


	@Transactional
	public HoldDetailResponse cancel(Long userId, Long holdId) {
		Long productId = holdFunction.getProductIdOfUserHold(userId, holdId);
		User user = userFunction.getByIdForUpdate(userId);
		Map<Long, Product> locked = lockProducts(List.of(productId));
		Hold hold = holdFunction.getByIdForUpdate(holdId);
		Instant now = Instant.now(clock);

		requireCancelable(hold, now);

		locked.get(productId).releaseHold(hold.getQty());
		hold.cancelByUser(now);
		chargeUnlessMisTap(hold, user, now);
		return HoldDetailResponse.of(List.of(hold), now, clock.getZone());
	}

	private void chargeUnlessMisTap(Hold hold, User user, Instant now) {
		if (hold.getCreatedAt().plus(holdProperties.freeCancelWindow()).isAfter(now)) {
			return;
		}
		int spent = settleCredits(user, now).spend(1);
		if (spent > 0) {
			holdCancelCreditFunction.record(
					user, hold, HoldCancelCreditReason.CANCEL, -spent, now);
		}
	}

	public HoldDetailResponse getHold(Long userId, Long holdId) {
		Instant now = Instant.now(clock);
		Hold hold = holdFunction.getDetailOfUserHold(userId, holdId);
		List<Hold> group = hold.getStatus() == HoldStatus.HOLDING
				? holdFunction.findHoldingOfGroup(hold.getGroupId())
				: List.of(hold);
		return HoldDetailResponse.of(group, now, clock.getZone());
	}

	public HoldHistoryResponse getHolds(Long userId, Pageable pageable) {
		Instant now = Instant.now(clock);
		Page<Hold> holds = holdFunction.findUserHolds(userId, pageable);
		// 어느 찜이 취소권을 먹었는지는 잔액만 봐서는 알 수 없다. 차감은 방금 한 행동과 무관한
		// 순간(밀린 노쇼 정산)에도 일어나므로, 행마다 표시해 줘야 숫자가 왜 줄었는지 읽힌다.
		Set<Long> charged = holdCancelCreditFunction.chargedHoldIdsAmong(
				holds.getContent().stream().map(Hold::getId).toList());
		List<HoldSummaryResponse> content = holds.getContent().stream()
				.map(hold -> HoldSummaryResponse.from(hold, now, charged.contains(hold.getId())))
				.toList();
		return new HoldHistoryResponse(now, PageResponse.of(content, holds));
	}

	public ActiveHoldResponse getActiveHold(Long userId) {
		Instant now = Instant.now(clock);
		HoldCancelCredit credit = creditsAsOf(userId, now);
		List<Hold> active = holdFunction.findActiveGroupOf(userId, now);
		return new ActiveHoldResponse(
				active.isEmpty() ? null : HoldDetailResponse.of(active, now, clock.getZone()),
				credit.getCredits(),
				credit.nextRefillAt(
						holdProperties.cancelCreditRefill(), holdProperties.cancelCreditMax()));
	}

	private HoldCancelCredit creditsAsOf(Long userId, Instant now) {
		HoldCancelCredit credit = holdCancelCreditFunction.findOf(userId)
				.orElseGet(() -> new HoldCancelCredit(
						null, holdProperties.cancelCreditMax(), now));
		credit.refill(now, holdProperties.cancelCreditRefill(), holdProperties.cancelCreditMax());
		credit.spend((int) holdFunction
				.findUnchargedNoShows(userId, now.minus(holdProperties.noShowGrace()))
				.stream()
				.map(Hold::getGroupId)
				.distinct()
				.count());
		return credit;
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

	// 밀려 있던 노쇼를 여기서 정산한다. 찜 하나당 한 번 깎고 그 찜을 가리키는 이력을 남긴다 --
	// 묶어서 한 번에 깎으면 나중에 "어느 찜 때문이었나"를 되짚을 수 없다.
	private HoldCancelCredit settleCredits(User user, Instant now) {
		HoldCancelCredit credit = holdCancelCreditFunction.getOrStart(
				user, holdProperties.cancelCreditMax(), now);
		int refilled = credit.refill(
				now, holdProperties.cancelCreditRefill(), holdProperties.cancelCreditMax());
		if (refilled > 0) {
			holdCancelCreditFunction.record(
					user, null, HoldCancelCreditReason.REFILL, refilled, now);
		}

		List<Hold> noShows = holdFunction.findUnchargedNoShows(
				user.getId(), now.minus(holdProperties.noShowGrace()));
		Set<Long> charged = new HashSet<>();
		for (Hold noShow : noShows) {
			// 잔액이 없어 못 깎아도 표시는 남긴다. 안 그러면 다음 정산에서 또 걸린다.
			noShow.markNoShowCharged(now);
			if (!charged.add(noShow.getGroupId())) {
				continue;
			}
			int spent = credit.spend(1);
			if (spent > 0) {
				holdCancelCreditFunction.record(
						user, noShow, HoldCancelCreditReason.NO_SHOW, -spent, now);
			}
		}
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
