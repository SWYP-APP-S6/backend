package com.swyp.backend.hold.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.dto.OwnerHoldCancelCandidatesResponse;
import com.swyp.backend.hold.dto.OwnerHoldCancelCandidatesResponse.OwnerHoldCancelCandidate;
import com.swyp.backend.hold.dto.OwnerHoldCancelCandidatesResponse.OwnerHoldCancelProduct;
import com.swyp.backend.hold.dto.OwnerHoldCancelRequest;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnerHoldCancelService {

	private static final String OWNER_SHORTAGE_REASON = "매장 재고가 모자라 찜이 취소됐어요.";
	private static final String NOTICE_TITLE = "찜이 취소됐어요";
	private static final String NOTICE_FORMAT =
			"[%s] 죄송합니다. 매장 재고 부족으로 인해 찜이 취소 되었습니다. 결제된 금액은 없습니다. %s";

	private final StoreFunction storeFunction;
	private final ProductFunction productFunction;
	private final HoldFunction holdFunction;
	private final NotificationFunction notificationFunction;
	private final Clock clock;

	public OwnerHoldCancelCandidatesResponse getCancelCandidates(Long ownerId) {
		return candidatesOf(storeFunction.getByOwnerId(ownerId));
	}

	@Transactional
	public OwnerHoldCancelCandidatesResponse cancelHolds(Long ownerId, OwnerHoldCancelRequest request) {
		Store store = storeFunction.getByOwnerId(ownerId);
		List<Long> holdIds = request.holdIds().stream().distinct().sorted().toList();
		Map<Long, Long> productIdByHold = holdFunction.getProductIdByHold(holdIds);

		Map<Long, Product> locked = new LinkedHashMap<>();
		productIdByHold.values().stream().distinct().sorted()
				.forEach(productId -> locked.put(productId, productFunction.getByIdForUpdate(productId)));
		if (locked.values().stream().anyMatch(product -> !product.getStore().getId().equals(store.getId()))) {
			throw new BusinessException(HoldErrorCode.HOLD_NOT_FOUND);
		}
		List<Hold> holds = holdIds.stream().map(holdFunction::getByIdForUpdate).toList();
		if (holds.stream().anyMatch(hold -> hold.getStatus() != HoldStatus.HOLDING)) {
			throw new BusinessException(HoldErrorCode.HOLD_ALREADY_RESOLVED);
		}
		Instant now = Instant.now(clock);
		if (holds.stream().anyMatch(hold -> hold.isOverdueAt(now))) {
			throw new BusinessException(HoldErrorCode.HOLD_ALREADY_EXPIRED);
		}
		if (locked.values().stream().anyMatch(product -> product.shortfallQty() <= 0)) {
			throw new BusinessException(HoldErrorCode.PRODUCT_NOT_SHORT_OF_STOCK);
		}

		String notice = noticeOf(store);
		for (Hold hold : holds) {
			hold.cancelByOwner(now, OWNER_SHORTAGE_REASON);
			locked.get(productIdByHold.get(hold.getId())).releaseHold(hold.getQty());
			notificationFunction.notify(
					hold.getUser(), NotificationType.HOLD_CANCELED_BY_OWNER, NOTICE_TITLE, notice, null);
		}
		return candidatesOf(store);
	}

	private OwnerHoldCancelCandidatesResponse candidatesOf(Store store) {
		Instant now = Instant.now(clock);
		List<Product> shortProducts = productFunction.findSellingNowOfStore(store.getId()).stream()
				.filter(product -> product.shortfallQty() > 0)
				.toList();
		List<Long> productIds = shortProducts.stream().map(Product::getId).toList();
		Map<Long, List<Hold>> holdsByProduct = holdFunction.findHoldingOfProducts(productIds).stream()
				.filter(hold -> !hold.isOverdueAt(now))
				.collect(Collectors.groupingBy(hold -> hold.getProduct().getId()));
		Map<Long, Integer> heldOrder = holdFunction.heldOrderOfProducts(productIds);

		List<OwnerHoldCancelProduct> products = new ArrayList<>();
		int suggestedCancelCount = 0;
		for (Product product : shortProducts) {
			List<Hold> inHeldOrder = holdsByProduct.getOrDefault(product.getId(), List.of());
			if (inHeldOrder.isEmpty()) {
				continue;
			}
			Set<Long> overflow = holdsThatDoNotFit(product, inHeldOrder);
			suggestedCancelCount += overflow.size();
			products.add(new OwnerHoldCancelProduct(
					product.getId(),
					product.getName(),
					product.getStockQty(),
					product.getHeldQty(),
					product.shortfallQty(),
					inHeldOrder.reversed().stream()
							.map(hold -> new OwnerHoldCancelCandidate(
									hold.getId(),
									heldOrder.get(hold.getId()),
									hold.getCreatedAt(),
									hold.getUser().getNickname(),
									hold.getQty(),
									product.getSalePrice() * hold.getQty(),
									overflow.contains(hold.getId())))
							.toList()));
		}
		return new OwnerHoldCancelCandidatesResponse(
				products.size(), suggestedCancelCount, noticeOf(store), products);
	}

	private static Set<Long> holdsThatDoNotFit(Product product, List<Hold> inHeldOrder) {
		int remaining = product.getStockQty();
		Set<Long> overflow = new HashSet<>();
		for (Hold hold : inHeldOrder) {
			if (hold.getQty() <= remaining) {
				remaining -= hold.getQty();
			} else {
				overflow.add(hold.getId());
			}
		}
		return overflow;
	}

	private static String noticeOf(Store store) {
		return NOTICE_FORMAT.formatted(store.getName(), displayPhone(store.getPhone()));
	}

	private static String displayPhone(String phone) {
		if (phone.contains("-")) {
			return phone;
		}
		int length = phone.length();
		if (phone.startsWith("02") && (length == 9 || length == 10)) {
			return phone.substring(0, 2) + "-" + phone.substring(2, length - 4) + "-" + phone.substring(length - 4);
		}
		if (length == 10 || length == 11) {
			return phone.substring(0, 3) + "-" + phone.substring(3, length - 4) + "-" + phone.substring(length - 4);
		}
		if (length == 8) {
			return phone.substring(0, 4) + "-" + phone.substring(4);
		}
		return phone;
	}
}
