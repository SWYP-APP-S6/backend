package com.swyp.backend.hold.service;

import com.swyp.backend.analytics.entity.DomainEvent;
import com.swyp.backend.analytics.entity.DomainEventType;
import com.swyp.backend.analytics.function.DomainEventFunction;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.hold.HoldProperties;
import com.swyp.backend.hold.dto.ActiveHoldResponse;
import com.swyp.backend.hold.dto.CancelCreditBalance;
import com.swyp.backend.hold.dto.HoldCreateRequest;
import com.swyp.backend.hold.dto.HoldDetailResponse;
import com.swyp.backend.hold.dto.HoldHistoryResponse;
import com.swyp.backend.hold.dto.HoldSummaryResponse;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCancelCreditReason;
import com.swyp.backend.hold.entity.HoldCanceledBy;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.exception.HoldErrorCode;
import com.swyp.backend.hold.function.HoldCancelCreditFunction;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.exception.ProductErrorCode;
import com.swyp.backend.product.function.ProductFunction;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.function.UserFunction;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HoldService {

	private final HoldCreator holdCreator;
	private final DomainEventFunction domainEventFunction;
	private final HoldFunction holdFunction;
	private final HoldCancelCreditFunction holdCancelCreditFunction;
	private final HoldCancelCreditSettler holdCancelCreditSettler;
	private final ProductFunction productFunction;
	private final UserFunction userFunction;
	private final HoldProperties holdProperties;
	private final Clock clock;

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public HoldDetailResponse create(Long userId, HoldCreateRequest request) {
		try {
			return holdCreator.create(userId, request);
		} catch (BusinessException e) {
			if (e.getCode() != ProductErrorCode.PRODUCT_NOT_FOUND) {
				recordFailure(userId, request, e);
			}
			throw e;
		}
	}

	private void recordFailure(Long userId, HoldCreateRequest request, BusinessException e) {
		Optional<Product> product = productFunction.findWithStoreById(request.productId());
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("code", e.getCode().name());
		payload.put("qty", request.qty());
		product.ifPresent(p -> {
			payload.put("storeName", p.getStore().getName());
			payload.put("productName", p.getName());
			payload.put("availableQty", p.getAvailableQty());
		});
		domainEventFunction.record(DomainEvent.builder()
				.eventType(DomainEventType.HOLD_FAIL)
				.userId(userId)
				.storeId(product.map(p -> p.getStore().getId()).orElse(null))
				.productId(request.productId())
				.payload(payload)
				.build());
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
		boolean charged = chargeUnlessMisTap(hold, user, now);
		domainEventFunction.record(DomainEventType.HOLD_CANCEL, hold, Map.of(
				"canceledBy", HoldCanceledBy.USER.name(),
				"charged", charged));
		return HoldDetailResponse.of(List.of(hold), now, clock.getZone());
	}

	private boolean chargeUnlessMisTap(Hold hold, User user, Instant now) {
		if (hold.getCreatedAt().plus(holdProperties.freeCancelWindow()).isAfter(now)) {
			return false;
		}
		int spent = holdCancelCreditSettler.settle(user, now).spend(1);
		if (spent > 0) {
			holdCancelCreditFunction.record(
					user, hold, HoldCancelCreditReason.CANCEL, -spent, now);
		}
		return spent > 0;
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
		CancelCreditBalance credit = holdCancelCreditFunction.balanceAsOf(userId, now);
		List<Hold> active = holdFunction.findActiveGroupOf(userId, now);
		return new ActiveHoldResponse(
				active.isEmpty() ? null : HoldDetailResponse.of(active, now, clock.getZone()),
				credit.credits(),
				credit.nextRefillAt());
	}

	private Map<Long, Product> lockProducts(List<Long> productIds) {
		Map<Long, Product> locked = new LinkedHashMap<>();
		productIds.stream().distinct().sorted()
				.forEach(id -> locked.put(id, productFunction.getByIdForUpdate(id)));
		return locked;
	}

	private static void requireCancelable(Hold hold, Instant now) {
		if (hold.getStatus() != HoldStatus.HOLDING) {
			throw new BusinessException(HoldErrorCode.HOLD_ALREADY_RESOLVED);
		}
		if (hold.isOverdueAt(now)) {
			throw new BusinessException(HoldErrorCode.HOLD_ALREADY_EXPIRED);
		}
	}
}
